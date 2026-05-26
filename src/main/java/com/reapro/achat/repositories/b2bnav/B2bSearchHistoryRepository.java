package com.reapro.achat.repositories.b2bnav;

import com.reapro.achat.entities.b2bnav.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface B2bSearchHistoryRepository extends JpaRepository<Customer, String> {

    interface B2bSearchHistoryProjection {
        Long getId();
        String getFilterDecoded();
        LocalDateTime getCreationDate();
        String getType();
        Long getCustomerId(); // NOUVEAU
        String getExtId();
        String getCompanyName();
        Integer getResultsCount();
        Boolean getIsStockAvailable();
    }

    @Query(value = """
        SELECT
          sh.id as id,
          convert_from(decode(sh."filter", 'base64'), 'UTF8') as filterDecoded,
          sh.creation_date as creationDate,
          sh.type as type,
          sh.customer_id as customerId,
          c.ext_id as extId,
          c.company_name as companyName,
          sh.results_count as resultsCount,
          sh.is_stock_available as isStockAvailable
        FROM public.search_history sh
        LEFT JOIN public.customer c ON c.id = sh.customer_id
        WHERE sh.id > :lastId
        ORDER BY sh.id ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<B2bSearchHistoryProjection> findNewRecordsAfterId(@Param("lastId") Long lastId, @Param("limit") int limit);
}
