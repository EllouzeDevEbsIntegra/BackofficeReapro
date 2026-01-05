package com.reapro.achat.repositories.sqlserver;

import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface LastInvoicedItemCostRepository extends JpaRepository<LastInvoicedItemCost, String> {

    // Pour enrichir bcItems en batch
    List<LastInvoicedItemCost> findByNoIn(Collection<String> nos);

    // Pour ton endpoint /api/last-invoiced-cost?itemNo=...
    List<LastInvoicedItemCost> findByNoOrderByLastInvoicedCostDateDesc(String no);
}