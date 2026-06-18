package com.reapro.achat.services;

import com.reapro.achat.DTO.pausegame.ManufacturerChallengeResponse;
import com.reapro.achat.DTO.pausegame.ManufacturerChoice;
import com.reapro.achat.DTO.pausegame.ManufacturerQuestion;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository.ManufacturerQuizItemProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Génère les questions du mini-jeu « Défi Fabricant » à partir de données RÉELLES Reapro
 * (table SQL Server ELVA_Item, lecture seule). Aucune donnée sensible exposée.
 *
 * Performance (préparation RAPIDE) :
 *  - requête projection légère SANS {@code ORDER BY NEWID()} (l'ancien tri full-scan provoquait ~35 s) ;
 *  - pool candidat mis en CACHE MÉMOIRE (TTL court) → les ouvertures suivantes sont instantanées ;
 *  - logos fabricants récupérés en UN appel TecDoc batché + cache mémoire (cf. TecDocService),
 *    avec timeout et fallback nom → jamais bloquant.
 *
 * Règles par question :
 *  - référence article réelle = {@code [Vendor Item No_]} (fallback {@code No_}) ;
 *  - articles « produit/master » exclus (Produit = 1, filtré en SQL) ;
 *  - exactement 4 choix de fabricants DISTINCTS, 1 seul correct, ordre mélangé ;
 *  - logo TecDoc si disponible (croisé via {@code [Tecdoc id fabricant]} = supplierId), sinon nom.
 */
@Service
@RequiredArgsConstructor
public class PauseGameService {

    private final ElvaItemRepository elvaItemRepository;
    private final TecDocService tecDocService;

    private static final int MIN_COUNT = 10;
    private static final int MAX_COUNT = 40;
    private static final int CHOICES = 4;
    private static final long POOL_TTL_MS = 5 * 60 * 1000L;   // cache pool 5 min

    /** Fabricant distinct : nom affiché + supplierId TecDoc (pour le logo). */
    private record Manufacturer(String display, Integer supplierId) {}

    // Cache mémoire du pool candidat (léger, partagé) → évite de retaper la base à chaque pause.
    private volatile List<ManufacturerQuizItemProjection> cachedPool = null;
    private volatile long cachedAt = 0L;

    @Transactional(readOnly = true)
    public ManufacturerChallengeResponse generateManufacturerChallenge(int requestedCount) {
        int count = Math.max(MIN_COUNT, Math.min(MAX_COUNT, requestedCount));

        List<ManufacturerQuizItemProjection> pool = getPool();
        if (pool.isEmpty()) {
            return new ManufacturerChallengeResponse(new ArrayList<>());
        }

        // Fabricants distincts (clé normalisée -> {nom, supplierId}).
        LinkedHashMap<String, Manufacturer> manufacturers = new LinkedHashMap<>();
        for (ManufacturerQuizItemProjection it : pool) {
            String display = normalizeDisplay(it.getFabricant());
            if (display == null) continue;
            String key = key(display);
            Manufacturer existing = manufacturers.get(key);
            Integer supplierId = parseSupplierId(it.getTecdocIdFabricant());
            if (existing == null) {
                manufacturers.put(key, new Manufacturer(display, supplierId));
            } else if (existing.supplierId() == null && supplierId != null) {
                manufacturers.put(key, new Manufacturer(existing.display(), supplierId)); // complète le supplierId
            }
        }
        List<String> allKeys = new ArrayList<>(manufacturers.keySet());
        if (allKeys.size() < CHOICES) {
            return new ManufacturerChallengeResponse(new ArrayList<>());
        }

        Random rnd = new Random();
        // Échantillonnage aléatoire des articles (variété entre parties sans tri SQL coûteux).
        List<ManufacturerQuizItemProjection> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, rnd);

        List<ManufacturerQuestion> questions = new ArrayList<>();
        Set<String> usedItems = new HashSet<>();

        for (ManufacturerQuizItemProjection it : shuffled) {
            if (questions.size() >= count) break;

            String no = it.getNo();
            if (no == null || no.isBlank() || !usedItems.add(no)) continue;

            String display = normalizeDisplay(it.getFabricant());
            if (display == null) continue;
            String correctKey = key(display);

            List<String> others = new ArrayList<>(allKeys);
            others.remove(correctKey);
            if (others.size() < CHOICES - 1) continue;
            Collections.shuffle(others, rnd);

            List<ManufacturerChoice> choices = new ArrayList<>(CHOICES);
            choices.add(new ManufacturerChoice(correctKey, display, null));
            for (int i = 0; i < CHOICES - 1; i++) {
                String k = others.get(i);
                choices.add(new ManufacturerChoice(k, manufacturers.get(k).display(), null));
            }
            Collections.shuffle(choices, rnd);

            // Référence affichée = Vendor Item No_ (fallback No_).
            String reference = firstNonBlank(it.getVendorItemNo(), no);
            String description = blankToNull(it.getDescription());

            questions.add(new ManufacturerQuestion(
                    no, reference, description, correctKey, display, choices));
        }

        enrichLogos(questions, manufacturers);
        return new ManufacturerChallengeResponse(questions);
    }

    /** Photo de la pièce (TecDoc) pour une référence — best-effort, caché, non bloquant. Peut être null. */
    public String getPartImage(String reference) {
        return tecDocService.getArticleImageUrl(reference);
    }

    /** Pool candidat mis en cache mémoire (TTL court) pour des ouvertures rapides. */
    private List<ManufacturerQuizItemProjection> getPool() {
        long now = System.currentTimeMillis();
        List<ManufacturerQuizItemProjection> snapshot = cachedPool;
        if (snapshot != null && (now - cachedAt) < POOL_TTL_MS) {
            return snapshot;
        }
        List<ManufacturerQuizItemProjection> fresh = elvaItemRepository.findManufacturerQuizCandidates();
        if (fresh == null) fresh = new ArrayList<>();
        cachedPool = fresh;
        cachedAt = now;
        return fresh;
    }

    /** Enrichit les choix avec le logo TecDoc (1 appel batché + cache), fallback nom si absent. */
    private void enrichLogos(List<ManufacturerQuestion> questions, Map<String, Manufacturer> manufacturers) {
        Set<Integer> supplierIds = new HashSet<>();
        for (ManufacturerQuestion q : questions) {
            for (ManufacturerChoice c : q.getChoices()) {
                Manufacturer m = manufacturers.get(c.getId());
                if (m != null && m.supplierId() != null) supplierIds.add(m.supplierId());
            }
        }
        if (supplierIds.isEmpty()) return;

        Map<Integer, String> logos = tecDocService.getSupplierLogos(supplierIds);
        if (logos.isEmpty()) return;

        for (ManufacturerQuestion q : questions) {
            for (ManufacturerChoice c : q.getChoices()) {
                Manufacturer m = manufacturers.get(c.getId());
                if (m != null && m.supplierId() != null) {
                    c.setLogoUrl(logos.get(m.supplierId()));   // null si pas de logo → fallback nom
                }
            }
        }
    }

    private static Integer parseSupplierId(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.isEmpty()) return null;
        try { return Integer.valueOf(s); } catch (NumberFormatException e) { return null; }
    }

    private static String normalizeDisplay(String raw) {
        if (raw == null) return null;
        String d = raw.trim().replaceAll("\\s+", " ");
        return d.isEmpty() ? null : d;
    }

    private static String key(String display) {
        return display.toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a;
        if (b != null && !b.isBlank()) return b;
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
