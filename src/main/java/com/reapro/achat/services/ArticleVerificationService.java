package com.reapro.achat.services;

import com.reapro.achat.DTO.ArticleVerificationResponse;
import com.reapro.achat.DTO.ArticleVerificationResponse.VerificationItem;
import com.reapro.achat.DTO.bc.BcItemBC;
import com.reapro.achat.DTO.bc.BcManufacturer;
import com.reapro.achat.DTO.tecdoc.TecDocApiResponse;
import com.reapro.achat.DTO.tecdoc.TecDocArticle;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import com.reapro.achat.repositories.primary.AdminRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleVerificationService {

    private final TecDocService tecDocService;
    private final BcManufacturerService manufacturerService;
    private final BusinessCentralService bcService;
    private final AdminRepository adminRepository;

    // Taille max du lot de références envoyées à BC
    private static final int CHUNK_SIZE = 10;

    public ArticleVerificationResponse verifyArticles(String email, String searchQuery) {

        // 1. Récupérer companyId depuis le profil
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        String companyId = admin.getBcCompanyId();
        if (companyId == null || companyId.isBlank()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Aucune société associée au profil");
        }

        // 2. Appel TecDoc (SearchType 1)
        TecDocApiResponse tecDocResp = tecDocService.searchArticlesType1(searchQuery);
        List<TecDocArticle> tecDocList = (tecDocResp != null && tecDocResp.getArticles() != null)
                ? tecDocResp.getArticles()
                : Collections.emptyList();

        if (tecDocList.isEmpty()) {
            return ArticleVerificationResponse.builder()
                    .totalTecDocItems(0)
                    .countEligible(0)
                    .countCreated(0)
                    .countNotCreated(0)
                    .items(List.of())
                    .build();
        }

        // 3. Récupérer Fabricants BC (caché)
        List<BcManufacturer> bcManufacturers = manufacturerService.getManufacturers(companyId);

        // Map rapide : IDTechDOC -> BcManufacturer
        Map<String, BcManufacturer> validManufacturers = bcManufacturers.stream()
                .filter(m -> m.getIdTechDoc() != null && !m.getIdTechDoc().isBlank())
                .collect(Collectors.toMap(BcManufacturer::getIdTechDoc, m -> m, (a, b) -> a));

        // 4. Filtrer TecDoc : conserver seulement les articles dont le dataSupplierId existe dans BC
        List<TecDocArticle> eligibleArticles = tecDocList.stream()
                .filter(a -> a.getDataSupplierId() != null
                        && validManufacturers.containsKey(String.valueOf(a.getDataSupplierId())))
                .toList();

        if (eligibleArticles.isEmpty()) {
            return ArticleVerificationResponse.builder()
                    .totalTecDocItems(tecDocList.size())
                    .countEligible(0)
                    .countCreated(0)
                    .countNotCreated(0)
                    .items(List.of())
                    .build();
        }

        // 5. Vérifier l'existence dans BC (Items) + récupérer No et ReferenceMaster
        Map<String, MatchInfo> existingItems = findExistingInBcBatch(companyId, eligibleArticles);

        // 6. Construire la réponse finale
        List<VerificationItem> resultItems = new ArrayList<>();
        int createdCount = 0;

        for (TecDocArticle art : eligibleArticles) {
            String key = makeKey(String.valueOf(art.getDataSupplierId()), art.getArticleNumber());
            MatchInfo match = existingItems.get(key);
            boolean isCreated = (match != null);
            if (isCreated) createdCount++;

            BcManufacturer bcMfr = validManufacturers.get(String.valueOf(art.getDataSupplierId()));
            String bcCode   = (bcMfr != null) ? bcMfr.getCode()     : null;
            String bcName   = (bcMfr != null) ? bcMfr.getName()     : null;
            String vendorNo = (bcMfr != null) ? bcMfr.getVendorNo() : null;

            resultItems.add(VerificationItem.builder()
                    .dataSupplierId(art.getDataSupplierId())
                    .manufacturerName(art.getMfrName())
                    // ✅ On renvoie la référence SANS ESPACES
                    .articleNumber(removeSpaces(art.getArticleNumber()))
                    .status(isCreated ? "CREATED" : "NOT_CREATED")
                    .articleDescription(
                            art.getGenericArticles() != null && !art.getGenericArticles().isEmpty()
                                    ? art.getGenericArticles().get(0).getGenericArticleDescription()
                                    : ""
                    )
                    .bcManufacturerCode(bcCode)
                    .bcManufacturerName(bcName)
                    .vendorNo(vendorNo)
                    // ✅ Nouveaux champs alimentés depuis BC
                    .bcItemNo(match != null ? match.getBcItemNo() : null)
                    .referenceMaster(match != null ? match.getReferenceMaster() : null)
                    .build());
        }

        return ArticleVerificationResponse.builder()
                .totalTecDocItems(tecDocList.size())
                .countEligible(eligibleArticles.size())
                .countCreated(createdCount)
                .countNotCreated(eligibleArticles.size() - createdCount)
                .items(resultItems)
                .build();
    }

    /**
     * Interroge BC par lots pour savoir quels articles existent déjà,
     * et récupère pour chacun :
     *  - No (bcItemNo)
     *  - ReferenceMaster
     *
     * Map retournée : key "mfrId|REF_NORMALISÉE" -> MatchInfo
     */
    private Map<String, MatchInfo> findExistingInBcBatch(String companyId, List<TecDocArticle> articles) {
        Map<String, MatchInfo> found = new HashMap<>();

        // Map ref normalisée -> set d'ID fournisseurs attendus
        Map<String, Set<String>> refToExpectedMfrs = new HashMap<>();

        // Références utilisées pour le $filter OData (brutes + sans espaces)
        Set<String> filterRefs = new LinkedHashSet<>();

        for (TecDocArticle a : articles) {
            String raw = safe(a.getArticleNumber()).trim(); // forme brute TecDoc
            if (raw.isEmpty()) continue;

            String norm = normalizeRef(raw); // sans espaces, MAJ

            // Pour le filtre OData, on ajoute :
            //  - la forme brute
            //  - la forme sans espaces
            filterRefs.add(raw);
            filterRefs.add(norm);

            // Pour la logique métier, on ne garde que la forme normalisée
            refToExpectedMfrs
                    .computeIfAbsent(norm, k -> new HashSet<>())
                    .add(String.valueOf(a.getDataSupplierId()));
        }

        List<String> uniqueFilterRefs = new ArrayList<>(filterRefs);

        // Traitement par lots
        for (int i = 0; i < uniqueFilterRefs.size(); i += CHUNK_SIZE) {
            List<String> chunkRefs = uniqueFilterRefs.subList(i, Math.min(i + CHUNK_SIZE, uniqueFilterRefs.size()));

            // Filtre BC sur les refs (brutes + sans espaces)
            StringBuilder filter = new StringBuilder();
            for (int j = 0; j < chunkRefs.size(); j++) {
                if (j > 0) filter.append(" or ");
                filter.append("VendorItemNo eq '").append(escape(chunkRefs.get(j))).append("'");
            }

            log.info("Check BC Batch - Filtre simplifié : {}", filter);

            Map<String, String> params = new HashMap<>();
            params.put("$filter", filter.toString());
            // ✅ On récupère aussi 'ReferenceMaster'
            params.put("$select", "manufacturerTecdocId,VendorItemNo,no,ReferenceMaster");

            try {
                ItemsKitService.BcItemsListResponse resp = bcService.getCustom(
                        "bcItems",
                        companyId,
                        params,
                        ItemsKitService.BcItemsListResponse.class
                );

                if (resp != null && resp.getValue() != null) {
                    for (BcItemBC item : resp.getValue()) {
                        String bcRefNorm = normalizeRef(item.getVendorItemNo());
                        String bcMfr     = item.getManufacturerTecdocId();

                        if (bcMfr != null && refToExpectedMfrs.containsKey(bcRefNorm)) {
                            Set<String> expectedMfrs = refToExpectedMfrs.get(bcRefNorm);
                            if (expectedMfrs.contains(bcMfr)) {
                                String key = makeKey(bcMfr, item.getVendorItemNo());
                                // On stocke No et ReferenceMaster
                                found.putIfAbsent(key, new MatchInfo(
                                        item.getNo(),
                                        item.getReferenceMaster()
                                ));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Erreur lors du check BC pour le lot : {}", e.getMessage());
            }
        }

        return found;
    }

    /**
     * Clé logique "ID_FABRICANT|REF_NORMALISÉE"
     */
    private String makeKey(String mfrId, String ref) {
        return (mfrId != null ? mfrId.trim() : "null") + "|" + normalizeRef(ref);
    }

    /**
     * Normalise une référence pour la comparaison logique TecDoc ↔ BC.
     * - supprime tous les espaces
     * - met en majuscule
     */
    private String normalizeRef(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toUpperCase();
    }

    /**
     * Pour l'affichage : supprime uniquement les espaces (sans changer la casse)
     */
    private String removeSpaces(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "");
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private String escape(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    // Petite classe interne pour stocker les infos BC utiles
    private static class MatchInfo {
        private final String bcItemNo;
        private final String referenceMaster;

        MatchInfo(String bcItemNo, String referenceMaster) {
            this.bcItemNo = bcItemNo;
            this.referenceMaster = referenceMaster;
        }

        public String getBcItemNo() {
            return bcItemNo;
        }

        public String getReferenceMaster() {
            return referenceMaster;
        }
    }
}