package com.reapro.achat.services;

import com.reapro.achat.DTO.bc.BcCountListResponse;
import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.DTO.bc.PurchaseCartLineBC;
import com.reapro.achat.DTO.bc.PurchaseCartLineCreateRequest;
import com.reapro.achat.DTO.bc.PurchaseCartLineUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseCartService {

    private final BusinessCentralService bcService;

    public static class BcListResponseWrapper extends BcListResponse<PurchaseCartLineBC> {}
    public static class BcCountListResponseWrapper extends BcCountListResponse<PurchaseCartLineBC> {}

    public BcListResponse<PurchaseCartLineBC> getPurchaseCartLines(String companyId, String compareQuoteNo, String status) {
        Map<String, String> queryParams = new HashMap<>();
        StringBuilder filter = new StringBuilder();

        // Construction du filtre : (statusPart) and compareQuoteNoPart
        // L'ordre est important pour les parenthèses si on a des OR dans le status.
        
        String statusPart = null;
        if (status != null && !status.isEmpty()) {
            if (status.contains(" eq ")) {
                 statusPart = "(" + status + ")";
            } else {
                statusPart = "status eq '" + status + "'";
            }
        }

        String compareQuotePart = null;
        if (compareQuoteNo != null && !compareQuoteNo.isEmpty()) {
            compareQuotePart = "compareQuoteNo eq '" + compareQuoteNo + "'";
        }

        if (statusPart != null) {
            filter.append(statusPart);
        }

        if (compareQuotePart != null) {
            if (filter.length() > 0) {
                filter.append(" and ");
            }
            filter.append(compareQuotePart);
        }

        if (filter.length() > 0) {
            queryParams.put("$filter", filter.toString());
        }

        return bcService.getCustom(
                "purchaseCartLines",
                companyId,
                queryParams,
                BcListResponseWrapper.class
        );
    }
    
    public long getPurchaseCartLinesCount(String companyId, String compareQuoteNo, String itemNo) {
        Map<String, String> queryParams = new HashMap<>();
        
        // Filtre de base : (status eq 'New' or status eq 'Verified') and compareQuoteNo eq '...'
        StringBuilder filter = new StringBuilder();
        filter.append(String.format("(status eq 'New' or status eq 'Verified') and compareQuoteNo eq '%s'", compareQuoteNo));
        
        // Ajout optionnel de itemNo
        if (itemNo != null && !itemNo.isEmpty()) {
            filter.append(String.format(" and itemNo eq '%s'", itemNo));
        }
        
        queryParams.put("$filter", filter.toString());
        queryParams.put("$count", "true");
        queryParams.put("$top", "0"); // On ne veut que le count, pas les données

        BcCountListResponseWrapper response = bcService.getCustom(
                "purchaseCartLines",
                companyId,
                queryParams,
                BcCountListResponseWrapper.class
        );
        
        return response != null && response.getCount() != null ? response.getCount() : 0;
    }

    /**
     * Récupère toutes les lignes du panier pour un compareQuoteNo donné,
     * filtrées par status (New ou Verified).
     * Retourne une Map<ItemNo, PurchaseCartLineBC>.
     * En cas de doublons (plusieurs lignes pour le même item), on prend la première (Top 1 implicite par la map).
     */
    public Map<String, PurchaseCartLineBC> getPurchaseCartLinesMap(String companyId, String compareQuoteNo) {
        if (compareQuoteNo == null || compareQuoteNo.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, String> queryParams = new HashMap<>();
        // Filtre : compareQuoteNo AND (status eq 'New' or status eq 'Verified')
        String filter = String.format("compareQuoteNo eq '%s' and (status eq 'New' or status eq 'Verified')", compareQuoteNo);
        queryParams.put("$filter", filter);
        
        // On ne limite pas le nombre de résultats (ou une limite raisonnable si besoin)
        // BC pagine par défaut, il faudrait gérer la pagination si > 20000 lignes, mais pour un panier c'est rare.
        
        BcListResponseWrapper response = bcService.getCustom(
                "purchaseCartLines",
                companyId,
                queryParams,
                BcListResponseWrapper.class
        );

        if (response == null || response.getValue() == null) {
            return Collections.emptyMap();
        }

        // Conversion en Map. Si doublon de clé (itemNo), on garde l'existant (a, b) -> a
        return response.getValue().stream()
                .filter(line -> line.getItemNo() != null)
                .collect(Collectors.toMap(
                        PurchaseCartLineBC::getItemNo,
                        line -> line,
                        (existing, replacement) -> existing
                ));
    }

    public PurchaseCartLineBC addPurchaseCartLine(String companyId, PurchaseCartLineCreateRequest request) {
        return bcService.postCustom(
                "purchaseCartLines",
                companyId,
                request,
                PurchaseCartLineBC.class
        );
    }

    public PurchaseCartLineBC updatePurchaseCartLine(String companyId, Integer lineNo, PurchaseCartLineUpdateRequest request) {
        if (request.getStatus() != null && "Converted to Quote".equalsIgnoreCase(request.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status 'Converted to Quote' is not allowed for update.");
        }

        String newStatus = request.getStatus();
        request.setStatus(null); 

        PurchaseCartLineBC result = null;

        // Vérifier si on a des champs à mettre à jour via PATCH (quantity, directUnitCost, comment)
        boolean hasPatchFields = request.getQuantity() != null 
                              || request.getDirectUnitCost() != null 
                              || request.getComment() != null;

        if (hasPatchFields) {
            PurchaseCartLineBC existingLine = bcService.getCustom(
                    "purchaseCartLines(" + lineNo + ")",
                    companyId,
                    null,
                    PurchaseCartLineBC.class
            );

            if (existingLine == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Purchase Cart Line not found: " + lineNo);
            }

            String etag = existingLine.getEtag();
            if (etag == null || etag.isBlank()) {
                etag = "*";
            }

            result = bcService.patchCustom(
                    "purchaseCartLines",
                    companyId,
                    String.valueOf(lineNo),
                    request,
                    etag,
                    PurchaseCartLineBC.class
            );
        }

        if (newStatus != null) {
            String action = null;
            if ("Verified".equalsIgnoreCase(newStatus)) {
                action = "Microsoft.NAV.verify";
            } else if ("Cancelled".equalsIgnoreCase(newStatus)) {
                action = "Microsoft.NAV.cancel";
            }

            if (action != null) {
                String endpoint = String.format("purchaseCartLines(%d)/%s", lineNo, action);
                Map<String, Object> body = new HashMap<>();
                
                result = bcService.postCustom(
                        endpoint,
                        companyId,
                        body,
                        PurchaseCartLineBC.class
                );
            } else if (!"New".equalsIgnoreCase(newStatus)) {
                 log.warn("Status update requested to '{}' but no action mapped (ignoring if New).", newStatus);
            }
        }

        if (result == null) {
             result = bcService.getCustom(
                    "purchaseCartLines(" + lineNo + ")",
                    companyId,
                    null,
                    PurchaseCartLineBC.class
            );
        }

        return result;
    }
}
