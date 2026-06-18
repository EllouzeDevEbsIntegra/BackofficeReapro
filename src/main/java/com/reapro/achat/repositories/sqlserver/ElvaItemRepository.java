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

    // Mini-jeu « Défi Fabricant » : candidats LÉGERS (projection 5 colonnes, pas SELECT *) ayant
    // un fabricant connu. Exclusions (au niveau SQL → rapide) :
    //   - références « produit/master » (Produit = 1) ;
    //   - références OEM (isOEM = 1) → ni la réf ni son fabricant ne servent (le pool de fabricants
    //     est bâti UNIQUEMENT sur ces lignes propres, donc aucun fabricant OEM dans les choix) ;
    //   - [Tecdoc id fabricant] doit être un entier 0 < id < 10000 (supplier TecDoc réel → logo
    //     quasi toujours disponible). TRY_CAST écarte proprement les valeurs non numériques/vides/nulles.
    // PAS d'ORDER BY NEWID() (full scan + tri = très lent) → variété gérée par échantillonnage côté service.
    // TOP 1500 borne le coût → requête rapide.
    @Query(value = "SELECT TOP 1500 No_ AS no, [Vendor Item No_] AS vendorItemNo, Fabricant AS fabricant, " +
            "[Tecdoc id fabricant] AS tecdocIdFabricant, [Description] AS description " +
            "FROM ELVA_Item " +
            "WHERE Fabricant IS NOT NULL AND LTRIM(RTRIM(Fabricant)) <> '' AND No_ IS NOT NULL " +
            "AND (Produit IS NULL OR Produit <> '1') " +
            "AND (isOEM IS NULL OR isOEM <> '1') " +
            "AND TRY_CAST([Tecdoc id fabricant] AS INT) IS NOT NULL " +
            "AND TRY_CAST([Tecdoc id fabricant] AS INT) > 0 " +
            "AND TRY_CAST([Tecdoc id fabricant] AS INT) < 10000", nativeQuery = true)
    List<ManufacturerQuizItemProjection> findManufacturerQuizCandidates();

    interface ManufacturerQuizItemProjection {
        String getNo();
        String getVendorItemNo();
        String getFabricant();
        String getTecdocIdFabricant();
        String getDescription();
    }

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
