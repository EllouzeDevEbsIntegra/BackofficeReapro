package com.reapro.achat.services;

import com.reapro.achat.DTO.bc.BcSalesOrderBC;
import com.reapro.achat.DTO.bc.BcSalesOrderLineBC;
import com.reapro.achat.entities.primary.SalesOrder;
import com.reapro.achat.entities.primary.SalesOrderLine;
import com.reapro.achat.enums.OrderStatus;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.repositories.primary.SalesOrderRepository;
import com.reapro.achat.services.bc.BusinessCentralSalesOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SalesOrderSyncBatch {

    private final SalesOrderRepository salesOrderRepository;
    private final BusinessCentralSalesOrderService bcSalesOrderService;

    /**
     * S'exécute toutes les heures (3600000 ms).
     * Synchronise les commandes locales validées avec Business Central.
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void syncValidatedOrdersWithBusinessCentral() {
        log.info("Démarrage du batch de synchronisation des commandes avec Business Central : {}", LocalDateTime.now());

        // Récupérer les commandes validées qui ont un numéro BC (créées localement).
        List<SalesOrder> ordersToSync = salesOrderRepository
                .findByStatusAndBusinessCentralOrderNumberIsNotNull(OrderStatus.VALIDATED);

        if (ordersToSync.isEmpty()) {
            log.info("Aucune commande à synchroniser.");
            return;
        }

        for (SalesOrder order : ordersToSync) {
            try {
                syncSingleOrder(order);
            } catch (Exception e) {
                log.error("Erreur lors de la synchronisation de la commande locale ID {}: {}", order.getId(), e.getMessage());
                // On continue avec la commande suivante sans bloquer le batch
            }
        }

        log.info("Fin du batch de synchronisation.");
    }

    private void syncSingleOrder(SalesOrder order) {
        String companyId = order.getCreatedBy().getBcCompanyId(); // CORRIGÉ: getBcCompanyId() au lieu de getBc_company_id()
        if (companyId == null || companyId.isBlank()) {
            log.warn("Commande locale ID {} ignorée: l'utilisateur créateur n'a pas de companyId", order.getId());
            return;
        }

        try {
            // Note: Since you use number for identifying order, maybe sync using number
            BcSalesOrderBC bcOrder = bcSalesOrderService.getSalesOrderByIdOrNumber(order.getBusinessCentralOrderNumber(), companyId);
            
            if (bcOrder == null) {
                log.info("La commande BC numéro {} (locale: {}) n'existe plus. Annulation locale.", order.getBusinessCentralOrderNumber(), order.getLocalNumber());
                cancelOrder(order);
                return;
            }

            // Mise à jour des lignes et calcul des totaux cumulés depuis les lignes
            java.math.BigDecimal totalExcludingTax = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalDiscount = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalTax = java.math.BigDecimal.ZERO;
            java.math.BigDecimal totalIncludingTax = java.math.BigDecimal.ZERO;

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
                        
                    } else {
                        // La ligne existait localement mais plus dans BC -> on l'annule sans la supprimer
                        log.info("La ligne {} n'est plus présente dans la commande BC {}. Annulation locale.", localLine.getItemReference(), order.getBusinessCentralOrderNumber());
                        localLine.setStatus(OrderStatus.CANCELLED);
                    }

                    if (localLine.getStatus() != OrderStatus.CANCELLED && localLine.getStatus() != OrderStatus.VALIDATION_ERROR) {
                        totalExcludingTax = totalExcludingTax.add(localLine.getAmountExcludingTax() != null ? localLine.getAmountExcludingTax() : java.math.BigDecimal.ZERO);
                        totalDiscount = totalDiscount.add(localLine.getDiscountAmount() != null ? localLine.getDiscountAmount() : java.math.BigDecimal.ZERO);
                        totalTax = totalTax.add(localLine.getVatAmount() != null ? localLine.getVatAmount() : java.math.BigDecimal.ZERO);
                        totalIncludingTax = totalIncludingTax.add(localLine.getAmountIncludingTax() != null ? localLine.getAmountIncludingTax() : java.math.BigDecimal.ZERO);
                    }
                }
            } else {
                // BC ne renvoie aucune ligne, on annule toutes les lignes locales
                order.getLines().forEach(l -> l.setStatus(OrderStatus.CANCELLED));
            }

            order.setTotalExcludingTax(totalExcludingTax);
            order.setTotalDiscount(totalDiscount);
            order.setTotalTax(totalTax);
            order.setTotalIncludingTax(totalIncludingTax);

            salesOrderRepository.save(order);
            log.info("Commande locale {} synchronisée avec succès.", order.getLocalNumber());

        } catch (ApiException e) {
            // Si l'API retourne une erreur 404 (ressource non trouvée), on annule la commande
            if (e.getErrorCode() != null && e.getErrorCode().getHttpStatus().value() == 404) {
                log.info("La commande BC numéro {} (locale: {}) a retourné 404. Annulation locale.", order.getBusinessCentralOrderNumber(), order.getLocalNumber());
                cancelOrder(order);
            } else {
                throw e; // Relancer pour logguer dans syncValidatedOrdersWithBusinessCentral
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
