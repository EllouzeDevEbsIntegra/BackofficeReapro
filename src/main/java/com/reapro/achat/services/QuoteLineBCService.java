package com.reapro.achat.services;

import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.DTO.bc.QuoteLineBC;
import com.reapro.achat.DTO.bc.QuoteLineUpdateRequest;
import com.reapro.achat.util.BcLineFilter;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuoteLineBCService {

    private final BusinessCentralService bcService;

    // ================== GET existant ==================

    public List<QuoteLineBC> getQuoteLines(String companyId, String compareQuoteNo, String referenceMaster) {

        String filter = String.format("CompareQuoteNo eq '%s' and ReferenceMaster eq '%s'",
                compareQuoteNo, referenceMaster);

        Map<String, String> params = Map.of("$filter", filter);

        BcListResponseWrapper response = bcService.getCustom(
                "quoteLines",
                companyId,
                params,
                BcListResponseWrapper.class
        );

        return response != null ? response.getValue() : Collections.emptyList();
    }

    /** Opérateurs de comparaison OData autorisés (whitelist anti-injection). */
    private static final Set<String> ALLOWED_OPERATORS = Set.of("gt", "ge", "eq", "le", "lt");

    /** Format de date attendu : yyyy-MM-dd (Edm.Date). */
    private static final java.util.regex.Pattern ISO_DATE = java.util.regex.Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private boolean isValidOperator(String op) {
        return op != null && ALLOWED_OPERATORS.contains(op);
    }

    /** Applique l'opérateur de comparaison sur le résultat d'un compareTo. */
    private boolean applyOperator(int cmp, String op) {
        switch (op) {
            case "gt": return cmp > 0;
            case "ge": return cmp >= 0;
            case "eq": return cmp == 0;
            case "le": return cmp <= 0;
            case "lt": return cmp < 0;
            default:   return true;
        }
    }

    /** Filtre numérique (availableInventory, quantity). Pas de filtre => true. Champ null + filtre actif => false. */
    private boolean matchesNumber(BigDecimal fieldValue, String operator, BigDecimal filterValue) {
        if (filterValue == null || !isValidOperator(operator)) return true;
        if (fieldValue == null) return false;
        return applyOperator(fieldValue.compareTo(filterValue), operator);
    }

    /** Filtre date (dateDernierAchat). Exclut les dates sentinelles (0001-01-01 / 1753-...) quand un filtre est actif. */
    private boolean matchesDate(LocalDate fieldValue, String operator, String filterValueIso) {
        if (filterValueIso == null || !isValidOperator(operator)
                || !ISO_DATE.matcher(filterValueIso.trim()).matches()) {
            return true;
        }
        if (fieldValue == null) return false;
        // Dates "vides" de BC -> exclues dès qu'un filtre date est demandé
        if (fieldValue.getYear() <= 1753) return false;
        LocalDate filterDate;
        try {
            filterDate = LocalDate.parse(filterValueIso.trim());
        } catch (Exception e) {
            return true; // valeur invalide -> filtre ignoré
        }
        return applyOperator(fieldValue.compareTo(filterDate), operator);
    }

    /**
     * GET : lignes de devis d'un comparateur (sans ReferenceMaster), triées par "no" croissant.
     *
     * IMPORTANT : availableInventory et dateDernierAchat sont des champs calculés (FlowFields)
     * que Business Central N'APPLIQUE PAS dans un $filter OData (ignorés silencieusement),
     * contrairement à quantity. Pour garantir un résultat correct, on récupère TOUTES les lignes
     * du comparateur (OData ne filtre que CompareQuoteNo, fiable), on applique les filtres
     * (stock/date/quantity) EN JAVA, on trie par "no", PUIS on pagine en Java.
     * Ainsi le filtrage est bien appliqué AVANT la pagination finale renvoyée au front.
     *
     * Opérateurs autorisés : gt(>), ge(>=), eq(=), le(<=), lt(<). Opérateur invalide => filtre ignoré.
     */
    public PagedQuoteLines getQuoteLinesByCompareQuote(
            String companyId, String compareQuoteNo, int page, int size,
            String stockOperator, BigDecimal stockValue,
            String dateDernierAchatOperator, String dateDernierAchatValue,
            String quantityOperator, BigDecimal quantityValue,
            String qtyFirstConfirmationOperator, BigDecimal qtyFirstConfirmationValue,
            String referenceOperator, String referenceValue) {

        int safeSize = size > 0 ? size : 20;
        int safePage = Math.max(page, 0);

        // 1) Récupération de TOUTES les lignes du comparateur (filtre OData fiable : CompareQuoteNo)
        Map<String, String> params = new java.util.HashMap<>();
        params.put("$filter", String.format("CompareQuoteNo eq '%s'", compareQuoteNo));
        params.put("$orderby", "no asc");

        BcListResponseWrapper response = bcService.getCustom(
                "quoteLines",
                companyId,
                params,
                BcListResponseWrapper.class
        );

        List<QuoteLineBC> all = (response != null && response.getValue() != null)
                ? response.getValue()
                : Collections.emptyList();

        // 2) Filtrage Java (availableInventory / dateDernierAchat / quantity)
        List<QuoteLineBC> filtered = new ArrayList<>();
        for (QuoteLineBC line : all) {
            if (matchesNumber(line.getAvailableInventory(), stockOperator, stockValue)
                    && matchesDate(line.getDateDernierAchat(), dateDernierAchatOperator, dateDernierAchatValue)
                    && matchesNumber(line.getQuantity(), quantityOperator, quantityValue)
                    && matchesNumber(line.getQtyFirstConfirmation(), qtyFirstConfirmationOperator, qtyFirstConfirmationValue)
                    && BcLineFilter.matchesReference(line.getNo(), referenceOperator, referenceValue)) {
                filtered.add(line);
            }
        }

        // 3) Tri stable par "no" croissant
        filtered.sort(Comparator.comparing(l -> l.getNo() == null ? "" : l.getNo()));

        // 4) Pagination Java (après filtrage). On expose AUSSI le total réel (avant pagination)
        //    pour que le frontend puisse afficher le badge "total de lignes" et calculer hasMore.
        int totalElements = filtered.size();
        int totalPages = (totalElements == 0) ? 0 : (int) Math.ceil((double) totalElements / safeSize);

        // Nombre distinct de N° DP (documentNo) sur la liste FILTRÉE COMPLÈTE (avant pagination)
        // → badge "DP" du header FRS. null/blank ignorés, trim appliqué.
        long distinctDocumentCount = filtered.stream()
                .map(QuoteLineBC::getDocumentNo)
                .filter(d -> d != null && !d.trim().isEmpty())
                .map(String::trim)
                .distinct()
                .count();

        int from = safePage * safeSize;
        List<QuoteLineBC> content;
        if (from >= totalElements) {
            content = Collections.emptyList();
            log.info("[ConfirmationAchat] compareQuoteNo={} total={} filtered={} page={} -> 0 ligne",
                    compareQuoteNo, all.size(), totalElements, safePage);
        } else {
            int to = Math.min(from + safeSize, totalElements);
            content = new ArrayList<>(filtered.subList(from, to));
            log.info("[ConfirmationAchat] compareQuoteNo={} total={} filtered={} page={} size={} -> {} ligne(s)",
                    compareQuoteNo, all.size(), totalElements, safePage, safeSize, (to - from));
        }

        return new PagedQuoteLines(content, totalElements, safePage, safeSize, totalPages, distinctDocumentCount);
    }

    public static class BcListResponseWrapper extends BcListResponse<QuoteLineBC> {}

    /**
     * Réponse paginée des lignes FRS du comparateur.
     * Structure alignée sur les endpoints EQV/KIT (content + totalElements + totalPages)
     * afin que le frontend exploite totalElements pour le badge et la pagination au scroll.
     */
    public static class PagedQuoteLines {
        private final List<QuoteLineBC> content;
        private final long totalElements;
        private final int page;
        private final int size;
        private final int totalPages;
        private final long distinctDocumentCount;

        public PagedQuoteLines(List<QuoteLineBC> content, long totalElements, int page, int size, int totalPages, long distinctDocumentCount) {
            this.content = content;
            this.totalElements = totalElements;
            this.page = page;
            this.size = size;
            this.totalPages = totalPages;
            this.distinctDocumentCount = distinctDocumentCount;
        }

        public List<QuoteLineBC> getContent() { return content; }
        public long getTotalElements() { return totalElements; }
        public int getPage() { return page; }
        public int getSize() { return size; }
        public int getTotalPages() { return totalPages; }
        public long getDistinctDocumentCount() { return distinctDocumentCount; }
    }


    // ================== PATCH AVEC GESTION ETag CÔTÉ BACKEND ==================

    /**
     * Met à jour une ligne de devis BC via PATCH.
     *
     * Étapes :
     *  1) GET companies(companyId)/quoteLines(id) pour récupérer @odata.etag
     *  2) PATCH companies(companyId)/quoteLines(id) avec If-Match = etag
     */
    public void updateQuoteLine(String companyId,
                                String id,
                                QuoteLineUpdateRequest req) {

        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id de ligne de devis obligatoire.");
        }

        // 1) Récupérer la ligne de devis pour récupérer l'ETag
        QuoteLineBC existing = bcService.getCustom(
                "quoteLines(" + id + ")",   // => /companies(companyId)/quoteLines(id)
                companyId,
                null,
                QuoteLineBC.class
        );

        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ligne de devis introuvable dans BC.");
        }

        String etag = existing.getEtag(); // Champ mappé avec @JsonProperty("@odata.etag") dans QuoteLineBC
        if (etag == null || etag.isBlank()) {
            // On log, mais on tente quand même le PATCH (BC refusera si vraiment obligatoire)
            log.warn("ETag absent sur la ligne de devis id={} (companyId={})", id, companyId);
        }

        try {
            // 2) PATCH en passant l'ETag dans If-Match
            bcService.patchCustom(
                    "quoteLines",
                    companyId,
                    id,
                    req,
                    etag,
                    Void.class   // PATCH 204 No Content
            );
        } catch (ApiException ex) {
            // Si BC renvoie un conflit d'ETag (modification concurrente), on peut propager un message plus clair
            throw ex;
        }
    }


    public BigDecimal getTotalAmountFromBC(String companyId, String documentNo) {
        // 1. Récupérer d'abord une ligne pour avoir un SystemId (requis pour une Bound Action)
        String filter = String.format("documentNo eq '%s'", documentNo);
        Map<String, String> params = java.util.Map.of(
                "$filter", filter,
                "$top", "1",
                "$select", "id"
        );

        BcListResponseWrapper lines = bcService.getCustom(
                "quoteLines",
                companyId,
                params,
                BcListResponseWrapper.class
        );

        if (lines == null || lines.getValue() == null || lines.getValue().isEmpty()) {
            log.warn("[BC-ACTION] Aucune ligne trouvée pour le document {}", documentNo);
            return BigDecimal.ZERO;
        }

        // 2. Extraire l'ID (SystemId) de la ligne de référence
        String lineId = lines.getValue().get(0).getId();

        // 3. Construire l'URL de l'Action (Respecter la casse validée : getTotalAmount)
        // L'URL relative sera complétée par BusinessCentralService
        String actionPath = String.format("quoteLines(%s)/Microsoft.NAV.getTotalAmount", lineId);

        // 4. Préparer le corps de la requête (Respecter la casse validée : documentNo)
        java.util.Map<String, Object> body = java.util.Map.of("documentNo", documentNo);

        try {
            log.info("[BC-ACTION] Appel getTotalAmount pour le document {}", documentNo);

            // 5. Appel POST vers BC via postCustom
            java.util.Map<String, Object> response = bcService.postCustom(
                    actionPath,
                    companyId,
                    body,
                    java.util.Map.class
            );

            if (response != null && response.containsKey("value")) {
                return new BigDecimal(response.get("value").toString());
            }
        } catch (Exception e) {
            log.error("[BC-ACTION] Erreur lors de l'appel de l'action BC : {}", e.getMessage());
        }

        return BigDecimal.ZERO;
    }
}