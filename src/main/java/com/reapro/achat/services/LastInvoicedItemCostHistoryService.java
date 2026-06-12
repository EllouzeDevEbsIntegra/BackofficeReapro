package com.reapro.achat.services;

import com.reapro.achat.DTO.LastInvoicedItemCostDTO;
import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import com.reapro.achat.repositories.sqlserver.LastInvoicedItemCostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Historique paginé des derniers coûts facturés d'un article
 * (vue View_ProjectReapro_LastInvoicedItemCost), trié par date décroissante.
 * Réutilise l'entité + repository existants. Distinct du service
 * LastInvoicedItemCostService (endpoint /api/last-invoiced-cost) qui reste inchangé.
 */
@Service
public class LastInvoicedItemCostHistoryService {

    private final LastInvoicedItemCostRepository repository;

    public LastInvoicedItemCostHistoryService(LastInvoicedItemCostRepository repository) {
        this.repository = repository;
    }

    public Page<LastInvoicedItemCostDTO> getByItemNo(String itemNo, Pageable pageable) {
        if (itemNo == null || itemNo.isBlank()) {
            throw new IllegalArgumentException("itemNo est obligatoire.");
        }
        return repository
                .findByNoOrderByLastInvoicedCostDateDesc(itemNo.trim(), pageable)
                .map(this::toDto);
    }

    private LastInvoicedItemCostDTO toDto(LastInvoicedItemCost e) {
        return new LastInvoicedItemCostDTO(
                e.getFrs(),
                e.getRefFrs(),
                e.getNo(),
                e.getLastInvoicedDirectCost(),
                e.getQuantity(),
                e.getLastInvoicedCostDate(),
                e.getSystemId());
    }
}
