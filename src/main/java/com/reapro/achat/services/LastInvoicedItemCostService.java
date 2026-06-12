package com.reapro.achat.services;

import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import com.reapro.achat.repositories.sqlserver.LastInvoicedItemCostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LastInvoicedItemCostService {

    private final LastInvoicedItemCostRepository repository;

    /**
     * Endpoint "dernier achat" (/api/last-invoiced-cost?itemNo=...). SIGNATURE INCHANGÉE
     * (retourne une List, comme avant) — consommé par le chargement secondaire FRS
     * (indexé par fournisseur 'frs') et l'enrichissement EQV/KIT.
     *
     * La vue View_ProjectReapro_LastInvoicedItemCost retourne désormais TOUTES les lignes
     * d'achat (plusieurs par No_ et par fournisseur). Pour rester compatible "dernier achat",
     * on ne conserve que la ligne la PLUS RÉCENTE PAR FOURNISSEUR (tri date DESC déjà appliqué
     * par le repository, donc la 1ère rencontrée par fournisseur est la plus récente).
     * NB : on ne renvoie PAS une seule ligne globale, sinon le Dernier Achat des autres
     * fournisseurs FRS (indexé par 'frs' côté front) deviendrait vide.
     */
    public List<LastInvoicedItemCost> getByItemNo(String itemNo) {
        if (itemNo == null || itemNo.isBlank()) {
            throw new IllegalArgumentException("itemNo est obligatoire.");
        }
        List<LastInvoicedItemCost> all =
                repository.findByNoOrderByLastInvoicedCostDateDesc(itemNo.trim()); // triées date DESC
        Map<String, LastInvoicedItemCost> latestPerVendor = new LinkedHashMap<>();
        for (LastInvoicedItemCost lc : all) {
            String vendorKey = (lc.getFrs() == null) ? "" : lc.getFrs().trim();
            latestPerVendor.putIfAbsent(vendorKey, lc); // 1ère = la plus récente pour ce fournisseur
        }
        return new ArrayList<>(latestPerVendor.values());
    }
}