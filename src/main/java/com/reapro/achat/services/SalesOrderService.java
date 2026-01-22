package com.reapro.achat.services;

import com.reapro.achat.DTO.bc.BcSalesOrderBC;
import com.reapro.achat.DTO.bc.BcSalesOrderLineBC;
import com.reapro.achat.DTO.salesorder.*;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.entities.primary.SalesOrder;
import com.reapro.achat.entities.primary.SalesOrderLine;
import com.reapro.achat.entities.sqlserver.ElvaItem;
import com.reapro.achat.enums.OrderStatus;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import com.reapro.achat.exceptions.StockValidationException;
import com.reapro.achat.repositories.primary.AdminRepository;
import com.reapro.achat.repositories.primary.SalesOrderLineRepository;
import com.reapro.achat.repositories.primary.SalesOrderRepository;
import com.reapro.achat.repositories.sqlserver.ElvaItemRepository;
import com.reapro.achat.services.bc.BusinessCentralSalesOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesOrderService {

    private final SalesOrderRepository salesOrderRepository;
    private final SalesOrderLineRepository salesOrderLineRepository;
    private final AdminRepository adminRepository;
    private final BusinessCentralSalesOrderService bcSalesOrderService;
    private final ElvaItemRepository elvaItemRepository;

    @Transactional(readOnly = true)
    public SalesOrderResponse getActiveCart(String email, String clientId) {
        Admin admin = getAdminByEmail(email);
        
        return salesOrderRepository.findByCreatedByAndClientIdAndStatus(admin, clientId, OrderStatus.DRAFT)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND, "Aucun panier brouillon trouvé pour ce client."));
    }

    @Transactional
    public SalesOrderResponse createCart(String email, CreateSalesOrderRequest request) {
        Admin admin = getAdminByEmail(email);

        return salesOrderRepository.findByCreatedByAndClientIdAndStatus(admin, request.getClientId(), OrderStatus.DRAFT)
                .map(this::mapToResponse)
                .orElseGet(() -> {
                    SalesOrder newOrder = SalesOrder.builder()
                            .clientId(request.getClientId())
                            .createdBy(admin)
                            .status(OrderStatus.DRAFT)
                            .build();
                    SalesOrder savedOrder = salesOrderRepository.save(newOrder);
                    return mapToResponse(savedOrder);
                });
    }

    @Transactional
    public SalesOrderResponse addLineToCart(String email, Long orderId, AddSalesOrderLineRequest request) {
        SalesOrder order = getDraftOrder(orderId, email);

        RealItemInfo itemInfo = fetchRealItemInfo(request.getReference());
        if (itemInfo == null) {
            throw new ApiException(ErrorCode.ITEM_NOT_FOUND, "L'article '" + request.getReference() + "' n'existe pas.");
        }

        int stock = itemInfo.getAvailableStock();
        if (request.getQuantity() > stock) {
            throw new ApiException(ErrorCode.STOCK_INSUFFICIENT,
                "Stock insuffisant pour '" + request.getReference() + "' : demandé " + request.getQuantity() + ", disponible " + stock + ".");
        }

        SalesOrderLine line = salesOrderLineRepository.findBySalesOrderIdAndItemReference(order.getId(), request.getReference())
                .orElse(null);

        if (line != null) {
            int newQuantity = line.getQuantity() + request.getQuantity();
            if (newQuantity > stock) {
                throw new ApiException(ErrorCode.STOCK_INSUFFICIENT,
                    "Quantité cumulée (" + newQuantity + ") dépasse le stock disponible (" + stock + ") pour '" + request.getReference() + "'.");
            }
            line.setQuantity(newQuantity);
        } else {
            line = SalesOrderLine.builder()
                    .salesOrder(order)
                    .itemReference(itemInfo.getReference())
                    .designation(itemInfo.getDesignation())
                    .brand(itemInfo.getBrand())
                    .unit(itemInfo.getUnit())
                    .quantity(request.getQuantity())
                    .unitPrice(itemInfo.getUnitPrice())
                    .availableStock(stock)
                    .status(OrderStatus.DRAFT)
                    .discountPercent(BigDecimal.ZERO)
                    .vatPercent(BigDecimal.valueOf(19)) // default 19%
                    .build();
            order.getLines().add(line);
        }

        salesOrderLineRepository.save(line);
        recalculateOrderTotals(order);
        return mapToResponse(salesOrderRepository.save(order));
    }

    @Transactional
    public SalesOrderResponse updateLineQuantity(String email, Long orderId, Long lineId, UpdateSalesOrderLineQuantityRequest request) {
        SalesOrder order = getDraftOrder(orderId, email);

        SalesOrderLine line = salesOrderLineRepository.findById(lineId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_LINE_NOT_FOUND));

        if (!line.getSalesOrder().getId().equals(order.getId())) {
            throw new ApiException(ErrorCode.ORDER_LINE_NOT_FOUND, "La ligne n'appartient pas à ce panier.");
        }

        // Récupérer le stock réel depuis ElvaItem (SQL Server)
        RealItemInfo itemInfo = fetchRealItemInfo(line.getItemReference());
        int availableStock = (itemInfo != null) ? itemInfo.getAvailableStock() : line.getAvailableStock();
        line.setAvailableStock(availableStock);

        if (request.getQuantity() > availableStock) {
            throw new ApiException(ErrorCode.STOCK_INSUFFICIENT,
                "Qté commandée " + request.getQuantity() + ", stock disponible : " + availableStock + " pour '" + line.getItemReference() + "'.");
        }

        line.setQuantity(request.getQuantity());
        salesOrderLineRepository.save(line);
        recalculateOrderTotals(order);
        return mapToResponse(salesOrderRepository.save(order));
    }

    @Transactional
    public SalesOrderResponse deleteLine(String email, Long orderId, Long lineId) {
        SalesOrder order = getDraftOrder(orderId, email);

        SalesOrderLine line = salesOrderLineRepository.findById(lineId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_LINE_NOT_FOUND));

        if (!line.getSalesOrder().getId().equals(order.getId())) {
            throw new ApiException(ErrorCode.ORDER_LINE_NOT_FOUND, "La ligne n'appartient pas à ce panier.");
        }

        // Supprimer uniquement cette ligne (pas l'ordre entier)
        order.getLines().remove(line);
        salesOrderLineRepository.deleteById(lineId);
        salesOrderLineRepository.flush();

        recalculateOrderTotals(order);
        return mapToResponse(salesOrderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public Page<SalesOrderResponse> getOrderHistory(
            String email, 
            String clientId, 
            com.reapro.achat.enums.OrderStatus status, 
            LocalDateTime dateFrom, 
            LocalDateTime dateTo, 
            Pageable pageable) {
        Admin admin = getAdminByEmail(email);

        org.springframework.data.jpa.domain.Specification<SalesOrder> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            // Toujours filtrer par créateur (admin)
            predicates.add(cb.equal(root.get("createdBy"), admin));

            // Filtrer par client
            if (clientId != null && !clientId.trim().isEmpty()) {
                predicates.add(cb.equal(root.get("clientId"), clientId));
            }

            // Filtrer par statut
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            // Filtrer par date début (supérieure ou égale)
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
            }

            // Filtrer par date fin (inférieure ou égale)
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
            }

            // Trier par date de création descendante (uniquement pour le select, pas pour le count)
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.orderBy(cb.desc(root.get("createdAt")));
            }

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return salesOrderRepository.findAll(spec, pageable)
                .map(this::mapToResponse);
    }

    @Transactional
    public SalesOrderResponse getOrderById(String email, Long orderId) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));
        
        if (order.getStatus() == OrderStatus.VALIDATED && order.getBusinessCentralOrderNumber() != null) {
            try {
                syncSingleOrder(order);
            } catch (Exception e) {
                log.error("Erreur de synchronisation en temps réel pour la commande {}", order.getBusinessCentralOrderNumber(), e);
            }
        }
        return mapToResponse(order);
    }

    @Transactional
    public SalesOrderResponse validateOrder(String email, Long orderId) {
        Admin admin = getAdminByEmail(email);
        SalesOrder order = getDraftOrder(orderId, email);

        if (order.getLines() == null || order.getLines().isEmpty()) {
            throw new ApiException(ErrorCode.ORDER_EMPTY, "Impossible de valider un panier vide.");
        }

        // 1. Contrôle final du stock
        List<InvalidStockLineDto> invalidLines = new ArrayList<>();
        for (SalesOrderLine line : order.getLines()) {
            RealItemInfo itemInfo = fetchRealItemInfo(line.getItemReference());
            if (itemInfo == null || line.getQuantity() > itemInfo.getAvailableStock()) {
                invalidLines.add(InvalidStockLineDto.builder()
                        .reference(line.getItemReference())
                        .orderedQuantity(line.getQuantity())
                        .availableQuantity(itemInfo != null ? itemInfo.getAvailableStock() : 0)
                        .build());
            }
        }

        if (!invalidLines.isEmpty()) {
            throw new StockValidationException("Certaines lignes dépassent le stock disponible", invalidLines);
        }

        // 2. Appel à Business Central
        try {
            String companyId = admin.getBcCompanyId(); // CORRIGÉ: getBcCompanyId() au lieu de getBc_company_id()
            if (companyId == null || companyId.isBlank()) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "L'utilisateur n'a pas de compagnie BC assignée.");
            }

            BcSalesOrderBC bcOrder = bcSalesOrderService.createSalesOrder(order, companyId);

            // 3. Mise à jour de la commande locale
            order.setStatus(OrderStatus.VALIDATED);
            order.setValidatedAt(LocalDateTime.now());
            order.setValidatedBy(admin);
            order.setBusinessCentralOrderNumber(bcOrder.getNumber());
            order.setBusinessCentralSystemId(bcOrder.getId());

            // 4. Mise à jour des lignes avec les infos de BC et calcul des totaux cumulés depuis les lignes
            BigDecimal totalExcludingTax = BigDecimal.ZERO;
            BigDecimal totalDiscount = BigDecimal.ZERO;
            BigDecimal totalTax = BigDecimal.ZERO;
            BigDecimal totalIncludingTax = BigDecimal.ZERO;

            if (bcOrder.getSalesOrderLines() != null) {
                Map<String, BcSalesOrderLineBC> bcLineMap = bcOrder.getSalesOrderLines().stream()
                        .filter(l -> l.getLineDetails() != null && l.getLineDetails().getNumber() != null)
                        .collect(Collectors.toMap(l -> l.getLineDetails().getNumber(), l -> l, (a, b) -> a)); // Handle duplicates if any

                for (SalesOrderLine localLine : order.getLines()) {
                    BcSalesOrderLineBC bcLine = bcLineMap.get(localLine.getItemReference());
                    if (bcLine != null) {
                        localLine.setStatus(OrderStatus.VALIDATED);
                        localLine.setBusinessCentralSystemId(bcLine.getId());
                        localLine.setBusinessCentralLineNumber(bcLine.getSequence() != null ? String.valueOf(bcLine.getSequence()) : null);
                        localLine.setUnitPrice(bcLine.getUnitPrice());
                        localLine.setDiscountPercent(bcLine.getDiscountPercent());
                        localLine.setDiscountAmount(bcLine.getDiscountAmount());
                        localLine.setAmountExcludingTax(bcLine.getAmountExcludingTax());
                        localLine.setVatPercent(bcLine.getTaxPercent());
                        localLine.setVatAmount(bcLine.getTotalTaxAmount());
                        localLine.setAmountIncludingTax(bcLine.getAmountIncludingTax());
                    } else {
                        // If line was not returned by BC, we might want to flag it or throw an error
                        log.warn("Line with reference {} not found in BC response", localLine.getItemReference());
                        localLine.setStatus(OrderStatus.VALIDATION_ERROR);
                    }

                    if (localLine.getStatus() != OrderStatus.CANCELLED && localLine.getStatus() != OrderStatus.VALIDATION_ERROR) {
                        totalExcludingTax = totalExcludingTax.add(localLine.getAmountExcludingTax() != null ? localLine.getAmountExcludingTax() : BigDecimal.ZERO);
                        totalDiscount = totalDiscount.add(localLine.getDiscountAmount() != null ? localLine.getDiscountAmount() : BigDecimal.ZERO);
                        totalTax = totalTax.add(localLine.getVatAmount() != null ? localLine.getVatAmount() : BigDecimal.ZERO);
                        totalIncludingTax = totalIncludingTax.add(localLine.getAmountIncludingTax() != null ? localLine.getAmountIncludingTax() : BigDecimal.ZERO);
                    }
                }
            }

            order.setTotalExcludingTax(totalExcludingTax);
            order.setTotalDiscount(totalDiscount);
            order.setTotalTax(totalTax);
            order.setTotalIncludingTax(totalIncludingTax);

            return mapToResponse(salesOrderRepository.save(order));

        } catch (ApiException e) {
            order.setStatus(OrderStatus.VALIDATION_ERROR);
            salesOrderRepository.save(order);
            throw e;
        } catch (Exception e) {
            log.error("Erreur inattendue lors de la validation BC", e);
            order.setStatus(OrderStatus.VALIDATION_ERROR);
            salesOrderRepository.save(order);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Erreur inattendue lors de la communication avec Business Central", e);
        }
    }


    // ==========================================
    // HELPER METHODS
    // ==========================================

    private Admin getAdminByEmail(String email) {
        return adminRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Utilisateur introuvable."));
    }

    private SalesOrder getDraftOrder(Long orderId, String email) {
        SalesOrder order = salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.ORDER_NOT_FOUND));

        if (order.getStatus() != OrderStatus.DRAFT) {
            throw new ApiException(ErrorCode.ORDER_NOT_DRAFT);
        }
        
        if (!order.getCreatedBy().getEmail().equals(email)) {
            throw new ApiException(ErrorCode.ORDER_NOT_FOUND, "Accès refusé à ce panier.");
        }

        return order;
    }

    private void recalculateOrderTotals(SalesOrder order) {
        BigDecimal totalExcludingTax = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalIncludingTax = BigDecimal.ZERO;

        for (SalesOrderLine line : order.getLines()) {
            BigDecimal qty = BigDecimal.valueOf(line.getQuantity());
            BigDecimal price = line.getUnitPrice() != null ? line.getUnitPrice() : BigDecimal.ZERO;
            
            // Calculate base amount excluding tax
            BigDecimal lineExcl = price.multiply(qty);
            line.setAmountExcludingTax(lineExcl);

            // Discount
            BigDecimal discPct = line.getDiscountPercent() != null ? line.getDiscountPercent() : BigDecimal.ZERO;
            BigDecimal lineDisc = lineExcl.multiply(discPct).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
            line.setDiscountPercent(discPct);
            line.setDiscountAmount(lineDisc);

            BigDecimal lineAfterDisc = lineExcl.subtract(lineDisc);

            // VAT
            BigDecimal vatPct = line.getVatPercent() != null ? line.getVatPercent() : BigDecimal.valueOf(19); // Default 19%
            BigDecimal lineVat = lineAfterDisc.multiply(vatPct).divide(BigDecimal.valueOf(100), 4, java.math.RoundingMode.HALF_UP);
            line.setVatPercent(vatPct);
            line.setVatAmount(lineVat);

            // Amount including tax
            BigDecimal lineIncl = lineAfterDisc.add(lineVat);
            line.setAmountIncludingTax(lineIncl);

            // Accumulate totals
            totalExcludingTax = totalExcludingTax.add(lineExcl);
            totalDiscount = totalDiscount.add(lineDisc);
            totalTax = totalTax.add(lineVat);
            totalIncludingTax = totalIncludingTax.add(lineIncl);
        }

        order.setTotalExcludingTax(totalExcludingTax);
        order.setTotalDiscount(totalDiscount);
        order.setTotalTax(totalTax);
        order.setTotalIncludingTax(totalIncludingTax);
    }

    private SalesOrderResponse mapToResponse(SalesOrder order) {
        List<SalesOrderLineResponse> lineResponses = new ArrayList<>();
        if (order.getLines() != null) {
            lineResponses = order.getLines().stream().map(line -> SalesOrderLineResponse.builder()
                    .id(line.getId())
                    .reference(line.getItemReference())
                    .designation(line.getDesignation())
                    .brand(line.getBrand())
                    .unit(line.getUnit())
                    .quantity(line.getQuantity())
                    .unitPrice(line.getUnitPrice())
                    .availableStock(line.getAvailableStock())
                    .status(line.getStatus())
                    .businessCentralLineNumber(line.getBusinessCentralLineNumber())
                    .businessCentralSystemId(line.getBusinessCentralSystemId())
                    .discountPercent(line.getDiscountPercent())
                    .discountAmount(line.getDiscountAmount())
                    .vatPercent(line.getVatPercent())
                    .vatAmount(line.getVatAmount())
                    .amountExcludingTax(line.getAmountExcludingTax())
                    .amountIncludingTax(line.getAmountIncludingTax())
                    .build()
            ).collect(Collectors.toList());
        }

        return SalesOrderResponse.builder()
                .id(order.getId())
                .localNumber(order.getLocalNumber())
                .status(order.getStatus())
                .clientId(order.getClientId())
                .createdAt(order.getCreatedAt())
                .createdBy(order.getCreatedBy() != null ? order.getCreatedBy().getFirstname() + " " + order.getCreatedBy().getLastname() : null)
                .validatedAt(order.getValidatedAt())
                .validatedBy(order.getValidatedBy() != null ? order.getValidatedBy().getFirstname() + " " + order.getValidatedBy().getLastname() : null)
                .businessCentralOrderNumber(order.getBusinessCentralOrderNumber())
                .businessCentralSystemId(order.getBusinessCentralSystemId())
                .totalExcludingTax(order.getTotalExcludingTax())
                .totalTax(order.getTotalTax())
                .totalIncludingTax(order.getTotalIncludingTax())
                .totalDiscount(order.getTotalDiscount())
                .lines(lineResponses)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    private static class RealItemInfo {
        private String reference;
        private String designation;
        private String brand;
        private String unit;
        private BigDecimal unitPrice;
        private int availableStock;
    }

    /**
     * Récupère les infos réelles de l'article depuis ElvaItem (SQL Server).
     * Le stock = quantite - reservedQuantity (arrondi à l'entier inférieur).
     */
    private RealItemInfo fetchRealItemInfo(String reference) {
        return elvaItemRepository.findByNo(reference).map(item -> {
            BigDecimal quantite = item.getQuantite() != null ? item.getQuantite() : BigDecimal.ZERO;
            BigDecimal reserved = item.getReservedQuantity() != null ? item.getReservedQuantity() : BigDecimal.ZERO;
            int availableStock = quantite.subtract(reserved).intValue();
            if (availableStock < 0) availableStock = 0;

            return RealItemInfo.builder()
                    .reference(item.getNo())
                    .designation(item.getDescriptionStructuree() != null ? item.getDescriptionStructuree() : item.getDescription())
                    .brand(item.getFabricant())
                    .unit(item.getBaseUnitOfMeasure())
                    .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                    .availableStock(availableStock)
                    .build();
        }).orElse(null);
    }

    private void syncSingleOrder(SalesOrder order) {
        String companyId = order.getCreatedBy().getBcCompanyId();
        if (companyId == null || companyId.isBlank()) {
            log.warn("Commande locale ID {} ignorée lors de la synchronisation : pas de companyId", order.getId());
            return;
        }

        try {
            BcSalesOrderBC bcOrder = bcSalesOrderService.getSalesOrderByIdOrNumber(order.getBusinessCentralOrderNumber(), companyId);
            
            if (bcOrder == null) {
                log.info("La commande BC numéro {} n'existe plus. Annulation locale.", order.getBusinessCentralOrderNumber());
                cancelOrder(order);
                return;
            }

            // Mise à jour des lignes et calcul des totaux cumulés depuis les lignes
            BigDecimal totalExcludingTax = BigDecimal.ZERO;
            BigDecimal totalDiscount = BigDecimal.ZERO;
            BigDecimal totalTax = BigDecimal.ZERO;
            BigDecimal totalIncludingTax = BigDecimal.ZERO;

            if (bcOrder.getSalesOrderLines() != null) {
                Map<String, BcSalesOrderLineBC> bcLineMap = bcOrder.getSalesOrderLines().stream()
                        .filter(l -> l.getLineDetails() != null && l.getLineDetails().getNumber() != null)
                        .collect(Collectors.toMap(l -> l.getLineDetails().getNumber(), l -> l, (a, b) -> a));

                for (SalesOrderLine localLine : order.getLines()) {
                    BcSalesOrderLineBC bcLine = bcLineMap.get(localLine.getItemReference());
                    
                    if (bcLine != null) {
                        localLine.setUnitPrice(bcLine.getUnitPrice());
                        localLine.setQuantity(bcLine.getQuantity() != null ? bcLine.getQuantity().intValue() : localLine.getQuantity());
                        localLine.setDiscountPercent(bcLine.getDiscountPercent());
                        localLine.setDiscountAmount(bcLine.getDiscountAmount());
                        localLine.setAmountExcludingTax(bcLine.getAmountExcludingTax());
                        localLine.setVatPercent(bcLine.getTaxPercent());
                        localLine.setVatAmount(bcLine.getTotalTaxAmount());
                        localLine.setAmountIncludingTax(bcLine.getAmountIncludingTax());
                        
                        // Si la ligne était marquée annulée ou erreur mais qu'elle existe dans BC
                        if (localLine.getStatus() == OrderStatus.CANCELLED || localLine.getStatus() == OrderStatus.VALIDATION_ERROR) {
                            localLine.setStatus(OrderStatus.VALIDATED);
                        }
                    } else {
                        // La ligne n'existe plus dans BC
                        log.info("La ligne {} n'est plus présente dans la commande BC {}. Annulation locale.", localLine.getItemReference(), order.getBusinessCentralOrderNumber());
                        localLine.setStatus(OrderStatus.CANCELLED);
                    }

                    if (localLine.getStatus() != OrderStatus.CANCELLED && localLine.getStatus() != OrderStatus.VALIDATION_ERROR) {
                        totalExcludingTax = totalExcludingTax.add(localLine.getAmountExcludingTax() != null ? localLine.getAmountExcludingTax() : BigDecimal.ZERO);
                        totalDiscount = totalDiscount.add(localLine.getDiscountAmount() != null ? localLine.getDiscountAmount() : BigDecimal.ZERO);
                        totalTax = totalTax.add(localLine.getVatAmount() != null ? localLine.getVatAmount() : BigDecimal.ZERO);
                        totalIncludingTax = totalIncludingTax.add(localLine.getAmountIncludingTax() != null ? localLine.getAmountIncludingTax() : BigDecimal.ZERO);
                    }
                }
            } else {
                order.getLines().forEach(l -> l.setStatus(OrderStatus.CANCELLED));
            }

            order.setTotalExcludingTax(totalExcludingTax);
            order.setTotalDiscount(totalDiscount);
            order.setTotalTax(totalTax);
            order.setTotalIncludingTax(totalIncludingTax);

            salesOrderRepository.save(order);
            log.info("Commande locale {} synchronisée avec succès avec BC.", order.getLocalNumber());

        } catch (ApiException e) {
            if (e.getErrorCode() != null && e.getErrorCode().getHttpStatus().value() == 404) {
                log.info("La commande BC numéro {} a retourné 404. Annulation locale.", order.getBusinessCentralOrderNumber());
                cancelOrder(order);
            } else {
                throw e;
            }
        }
    }

    private void cancelOrder(SalesOrder order) {
        order.setStatus(OrderStatus.CANCELLED);
        if (order.getLines() != null) {
            order.getLines().forEach(l -> l.setStatus(OrderStatus.CANCELLED));
        }
        salesOrderRepository.save(order);
    }
}
