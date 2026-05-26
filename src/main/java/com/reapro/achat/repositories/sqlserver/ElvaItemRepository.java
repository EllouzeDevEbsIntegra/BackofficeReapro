package com.reapro.achat.repositories.sqlserver;

import com.reapro.achat.entities.sqlserver.ElvaItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ElvaItemRepository extends JpaRepository<ElvaItem, String>, JpaSpecificationExecutor<ElvaItem> {

    @Query(value = "SELECT TOP 1000 * FROM ELVA_Item", nativeQuery = true)
    List<ElvaItem> findTop1000();

    Page<ElvaItem> findAll(Pageable pageable);

    Optional<ElvaItem> findByNo(String no);

    // OPTIMISATION : On ne récupère QUE les champs nécessaires au temps réel
    // On utilise une interface de projection (Spring Data Projection) pour éviter de mapper toute l'entité
    @Query(value = "SELECT No_ as no, [Unit Price] as unitPrice, Quantité as quantite, ReservedQuantity as reservedQuantity, reception_qty as receptionQty FROM ELVA_Item WHERE No_ IN :itemNos", nativeQuery = true)
    List<ElvaItemRealTimeProjection> findRealTimeDataByNos(@Param("itemNos") List<String> itemNos);

    interface ElvaItemRealTimeProjection {
        String getNo();
        BigDecimal getUnitPrice();
        BigDecimal getQuantite();
        BigDecimal getReservedQuantity();
        BigDecimal getReceptionQty();
    }
}
