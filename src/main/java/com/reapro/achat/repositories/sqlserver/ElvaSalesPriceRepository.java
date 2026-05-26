package com.reapro.achat.repositories.sqlserver;

import com.reapro.achat.entities.sqlserver.ElvaSalesPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ElvaSalesPriceRepository extends JpaRepository<ElvaSalesPrice, ElvaSalesPrice.ElvaSalesPriceId> {

    // Requête pour trouver le prix actif pour un article et un client donnés à une date précise
    // La condition (endingDate = '1753-01-01' ou similaire) est courante dans NAV/BC pour "pas de date de fin"
    // Je gère ici le fait que endingDate peut être null, ou très ancienne, ou future.
    @Query(value = "SELECT TOP 1 * FROM [Amiral_LS].[dbo].[ELVA_sales_price] " +
            "WHERE [Item No_] = :itemNo " +
            "AND [Sales Code] = :clientId " +
            "AND [Starting Date] <= :currentDate " +
            "AND ([Ending Date] IS NULL OR [Ending Date] >= :currentDate OR [Ending Date] < '1900-01-01') " +
            "ORDER BY [Starting Date] DESC", nativeQuery = true)
    ElvaSalesPrice findActivePriceForClient(
            @Param("itemNo") String itemNo,
            @Param("clientId") String clientId,
            @Param("currentDate") LocalDateTime currentDate);
            
    // Version optimisée pour récupérer les prix de plusieurs articles d'un coup
    @Query(value = "SELECT p.* FROM ( " +
            "    SELECT *, ROW_NUMBER() OVER(PARTITION BY [Item No_] ORDER BY [Starting Date] DESC) as rn " +
            "    FROM [Amiral_LS].[dbo].[ELVA_sales_price] " +
            "    WHERE [Item No_] IN (:itemNos) " +
            "    AND [Sales Code] = :clientId " +
            "    AND [Starting Date] <= :currentDate " +
            "    AND ([Ending Date] IS NULL OR [Ending Date] >= :currentDate OR [Ending Date] < '1900-01-01') " +
            ") p WHERE p.rn = 1", nativeQuery = true)
    List<ElvaSalesPrice> findActivePricesForClientAndItems(
            @Param("itemNos") List<String> itemNos,
            @Param("clientId") String clientId,
            @Param("currentDate") LocalDateTime currentDate);
}
