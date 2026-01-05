package com.reapro.achat.services;

import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.DTO.bc.QuoteLineBC;
import com.reapro.achat.DTO.bc.QuoteLineUpdateRequest;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    public static class BcListResponseWrapper extends BcListResponse<QuoteLineBC> {}


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