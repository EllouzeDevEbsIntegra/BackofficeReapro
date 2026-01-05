package com.reapro.achat.services;

import com.reapro.achat.entities.sqlserver.PurchasePrice;
import com.reapro.achat.repositories.sqlserver.PurchasePriceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchasePriceService {

    private final PurchasePriceRepository repository;

    public List<PurchasePrice> getPrices(String vendorNo, String itemNo) {
        if (itemNo == null || itemNo.isBlank()) {
            throw new IllegalArgumentException("itemNo est obligatoire.");
        }

        String item = itemNo.trim();

        if (vendorNo == null || vendorNo.isBlank()) {
            // ✅ vendorNo absent → on retourne tous les prix de l'article
            return repository.findByItemNoOrderByStartingDateDesc(item);
        }

        // vendorNo présent → filtre vendor + item
        return repository.findByVendorNoAndItemNoOrderByStartingDateDesc(vendorNo.trim(), item);
    }
}