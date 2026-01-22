package com.reapro.achat.services.bc;

import com.fasterxml.jackson.databind.JsonNode;
import com.reapro.achat.DTO.bc.*;
import com.reapro.achat.entities.primary.SalesOrder;
import com.reapro.achat.entities.primary.SalesOrderLine;
import com.reapro.achat.services.BusinessCentralService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessCentralSalesOrderService {

    private final BusinessCentralService bcService;

    public BcSalesOrderBC createSalesOrder(SalesOrder localOrder, String companyId) {
        log.info("Creating Sales Order in BC for local order: {}", localOrder.getLocalNumber());

        // 1. Resolve customerId (GUID) from customerNumber
        String customerId = resolveCustomerIdFromNumber(localOrder.getClientId(), companyId);

        // 2. Create Header
        BcSalesOrderCreateRequest headerRequest = BcSalesOrderCreateRequest.builder()
                .customerId(customerId)
                .customerNumber(localOrder.getClientId())
                .build();

        // Using the standard base URL and custom endpoint from the example
        BcSalesOrderBC createdHeader = bcService.postStandard(
                "salesOrdersEBS",
                companyId,
                headerRequest,
                BcSalesOrderBC.class
        );

        log.info("Created Sales Order Header in BC. BC ID: {}, BC No: {}", createdHeader.getId(), createdHeader.getNumber());

        // 2. Create Lines
        for (SalesOrderLine localLine : localOrder.getLines()) {
            
            // a) Find BC itemId by reference (ItemNo)
            String itemId = resolveItemIdFromReference(localLine.getItemReference(), companyId);
            if (itemId == null) {
                log.error("Could not find BC itemId for reference: {}", localLine.getItemReference());
                continue; // Or throw an exception to rollback the entire order creation
            }

            // b) Create the line
            BcSalesOrderLineCreateRequest lineRequest = BcSalesOrderLineCreateRequest.builder()
                    .lineType("Item")
                    .itemId(itemId) // Important: BC wants the GUID here based on your payload mapping, sometimes it expects the No in a specific field, but usually the itemId GUID
                    .quantity(BigDecimal.valueOf(localLine.getQuantity()))
                    .build();

            // Note: Use salesOrdersEBS as the parent entity set to match the header creation
            String linesEndpoint = "salesOrdersEBS(" + createdHeader.getId() + ")/salesOrderLines";
            
            try {
                BcSalesOrderLineBC createdLine = bcService.postStandard(
                        linesEndpoint,
                        companyId,
                        lineRequest,
                        BcSalesOrderLineBC.class
                );
                log.info("Created Sales Order Line in BC for item: {}", localLine.getItemReference());
            } catch (Exception e) {
                log.error("Failed to create line for item {} in order {}", localLine.getItemReference(), createdHeader.getNumber(), e);
                throw new RuntimeException("Erreur de création de la ligne dans BC pour " + localLine.getItemReference() + ": " + e.getMessage(), e);
            }
        }

        // 3. Fetch the fully created order to get updated totals
        return getSalesOrderByIdOrNumber(createdHeader.getNumber(), companyId);
    }

    public BcSalesOrderBC getSalesOrderByIdOrNumber(String bcOrderNumber, String companyId) {
        log.info("Fetching Sales Order from BC by Number: {}", bcOrderNumber);
        
        // $filter=number eq 'CV25-000742'&$expand=salesOrderLines
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put("$filter", "number eq '" + bcOrderNumber + "'");
        queryParams.put("$expand", "salesOrderLines");

        BcSalesOrderApiResponse response = bcService.getStandard(
                "salesOrdersEBS",
                companyId,
                queryParams,
                BcSalesOrderApiResponse.class
        );

        if (response != null && response.getValue() != null && !response.getValue().isEmpty()) {
            return response.getValue().get(0);
        }
        
        return null;
    }

    private String resolveItemIdFromReference(String reference, String companyId) {
        // From your example: SiItemAPI?$filter=Ref eq ('...')
        Map<String, String> params = new HashMap<>();
        params.put("$filter", "Ref eq '" + reference + "'");
        params.put("$select", "id");

        try {
            // Using JsonNode to quickly parse the response without creating a specific DTO
            JsonNode response = bcService.getCustom(
                    "SiItemAPI",
                    companyId,
                    params,
                    JsonNode.class
            );

            if (response.has("value") && response.get("value").isArray() && response.get("value").size() > 0) {
                return response.get("value").get(0).get("id").asText();
            }
        } catch (Exception e) {
            log.error("Error resolving itemId for reference: {}", reference, e);
        }
        return null;
    }

    private String resolveCustomerIdFromNumber(String customerNumber, String companyId) {
        Map<String, String> params = new HashMap<>();
        params.put("$filter", "number eq '" + customerNumber + "'");
        params.put("$select", "id");

        try {
            JsonNode response = bcService.getStandard(
                    "customers",
                    companyId,
                    params,
                    JsonNode.class
            );

            if (response != null && response.has("value") && response.get("value").isArray() && response.get("value").size() > 0) {
                return response.get("value").get(0).get("id").asText();
            }
        } catch (Exception e) {
            log.error("Error resolving customerId for number: {}", customerNumber, e);
        }
        return null;
    }
}
