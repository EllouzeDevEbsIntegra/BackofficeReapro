package com.reapro.achat.services;

import com.reapro.achat.DTO.bc.BcCountListResponse;
import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.DTO.bc.PurchaseCartLineBC;
import com.reapro.achat.DTO.bc.PurchaseCartLineCreateRequest;
import com.reapro.achat.DTO.bc.PurchaseCartLineUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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

    public Page<PurchaseCartLineBC> getPurchaseCartLines(String companyId, String compareQuoteNo, String status, String vendorNo, String itemNo, Pageable pageable) {
        // 1. Construire le filtre OData
        String filter = buildODataFilter(compareQuoteNo, status, vendorNo, itemNo);

        // 2. Obtenir le count total
        long totalCount = getFilteredCount(companyId, filter);

        // 3. Obtenir les données pour la page actuelle
        List<PurchaseCartLineBC> pageData = getPageData(companyId, filter, pageable);

        // 4. Construire et retourner l'objet Page
        return new PageImpl<>(pageData, pageable, totalCount);
    }

    private String buildODataFilter(String compareQuoteNo, String status, String vendorNo, String itemNo) {
        StringBuilder filter = new StringBuilder();
        
        // Status filter
        String statusPart = null;
        if (status != null && !status.isEmpty()) {
            if (status.contains(" eq ")) {
                 statusPart = "(" + status + ")";
            } else {
                statusPart = "status eq '" + status + "'";
            }
        }

        // CompareQuoteNo filter
        String compareQuotePart = null;
        if (compareQuoteNo != null && !compareQuoteNo.isEmpty()) {
            compareQuotePart = "compareQuoteNo eq '" + compareQuoteNo + "'";
        }

        // VendorNo filter
        String vendorNoPart = null;
        if (vendorNo != null && !vendorNo.isEmpty()) {
            vendorNoPart = "buyFromVendorNo eq '" + vendorNo + "'";
        }

        // ItemNo filter
        String itemNoPart = null;
        if (itemNo != null && !itemNo.isEmpty()) {
            itemNoPart = "itemNo eq '" + itemNo + "'";
        }

        // Append parts to the filter, adding " and " where necessary
        if (statusPart != null) {
            filter.append(statusPart);
        }

        if (compareQuotePart != null) {
            if (filter.length() > 0) {
                filter.append(" and ");
            }
            filter.append(compareQuotePart);
        }

        if (vendorNoPart != null) {
            if (filter.length() > 0) {
                filter.append(" and ");
            }
            filter.append(vendorNoPart);
        }

        if (itemNoPart != null) {
            if (filter.length() > 0) {
                filter.append(" and ");
            }
            filter.append(itemNoPart);
        }

        return filter.toString();
    }

    private long getFilteredCount(String companyId, String filter) {
        Map<String, String> queryParams = new HashMap<>();
        if (filter != null && !filter.isEmpty()) {
            queryParams.put("$filter", filter);
        }
        queryParams.put("$count", "true");
        queryParams.put("$top", "0");

        BcCountListResponseWrapper response = bcService.getCustom(
                "purchaseCartLines",
                companyId,
                queryParams,
                BcCountListResponseWrapper.class
        );
        return response != null && response.getCount() != null ? response.getCount() : 0;
    }

    private List<PurchaseCartLineBC> getPageData(String companyId, String filter, Pageable pageable) {
        Map<String, String> queryParams = new HashMap<>();
        if (filter != null && !filter.isEmpty()) {
            queryParams.put("$filter", filter);
        }
        queryParams.put("$top", String.valueOf(pageable.getPageSize()));
        queryParams.put("$skip", String.valueOf(pageable.getOffset()));

        // Note: OData sorting can be added here if needed, e.g., queryParams.put("$orderby", "creationDate desc");

        BcListResponseWrapper response = bcService.getCustom(
                "purchaseCartLines",
                companyId,
                queryParams,
                BcListResponseWrapper.class
        );
        return response != null ? response.getValue() : Collections.emptyList();
    }
    
    public long getPurchaseCartLinesCount(String companyId, String compareQuoteNo, String itemNo) {
        Map<String, String> queryParams = new HashMap<>();
        
        StringBuilder filter = new StringBuilder();
        filter.append(String.format("(status eq 'New' or status eq 'Verified') and compareQuoteNo eq '%s'", compareQuoteNo));
        
        if (itemNo != null && !itemNo.isEmpty()) {
            filter.append(String.format(" and itemNo eq '%s'", itemNo));
        }
        
        queryParams.put("$filter", filter.toString());
        queryParams.put("$count", "true");
        queryParams.put("$top", "0");

        BcCountListResponseWrapper response = bcService.getCustom(
                "purchaseCartLines",
                companyId,
                queryParams,
                BcCountListResponseWrapper.class
        );
        
        return response != null && response.getCount() != null ? response.getCount() : 0;
    }

    public Map<String, PurchaseCartLineBC> getPurchaseCartLinesMap(String companyId, String compareQuoteNo) {
        if (compareQuoteNo == null || compareQuoteNo.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, String> queryParams = new HashMap<>();
        String filter = String.format("compareQuoteNo eq '%s' and (status eq 'New' or status eq 'Verified')", compareQuoteNo);
        queryParams.put("$filter", filter);
        
        BcListResponseWrapper response = bcService.getCustom(
                "purchaseCartLines",
                companyId,
                queryParams,
                BcListResponseWrapper.class
        );

        if (response == null || response.getValue() == null) {
            return Collections.emptyMap();
        }

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
