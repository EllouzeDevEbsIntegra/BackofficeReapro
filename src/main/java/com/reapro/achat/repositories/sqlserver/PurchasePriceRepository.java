package com.reapro.achat.repositories.sqlserver;

import com.reapro.achat.entities.sqlserver.PurchasePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PurchasePriceRepository extends JpaRepository<PurchasePrice, String> {

    // WHERE VendorNo = ? AND ItemNo = ? ORDER BY StartingDate DESC
    List<PurchasePrice> findByVendorNoAndItemNoOrderByStartingDateDesc(String vendorNo, String itemNo);

    List<PurchasePrice> findByItemNoOrderByStartingDateDesc(String itemNo);
}