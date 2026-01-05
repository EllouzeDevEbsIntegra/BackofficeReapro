package com.reapro.achat.services;

import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import com.reapro.achat.repositories.sqlserver.LastInvoicedItemCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LastInvoicedItemCostService {

    private final LastInvoicedItemCostRepository repository;

    public List<LastInvoicedItemCost> getByItemNo(String itemNo) {
        if (itemNo == null || itemNo.isBlank()) {
            throw new IllegalArgumentException("itemNo est obligatoire.");
        }
        return repository.findByNoOrderByLastInvoicedCostDateDesc(itemNo.trim());
    }
}