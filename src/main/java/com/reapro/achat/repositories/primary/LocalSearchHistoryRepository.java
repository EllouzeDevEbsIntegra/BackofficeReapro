package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.LocalSearchHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public interface LocalSearchHistoryRepository extends JpaRepository<LocalSearchHistory, Long> {

    @Query(value = """
        WITH erp_refs AS (
            SELECT DISTINCT
                CASE
                    WHEN LOWER(COALESCE(e.produit, '')) IN ('true', '1', 't', 'yes', 'y')
                        THEN REPLACE(UPPER(REPLACE(e.no, ' ', '')), 'MASTER', '')
                    ELSE UPPER(REPLACE(e.no, ' ', ''))
                END AS ref_compact
            FROM elva_item_cache e
        ),
        page_rows AS (
            SELECT
                mv.normalized_filter AS normalizedFilter,
                mv.original_filter_example AS originalFilterExample,
                mv.total_attempts AS totalAttempts,
                mv.distinct_customers_count AS distinctCustomersCount,
                mv.first_search_date AS firstSearchDate,
                mv.last_search_date AS lastSearchDate,
                mv.latest_search_id AS latestSearchId,
                mv.zero_result_count AS zeroResultCount,
                mv.no_stock_count AS noStockCount,
                mv.with_stock_count AS withStockCount,
                mv.priority_score AS priorityScore,
                COUNT(*) OVER() AS totalCount
            FROM search_opportunity_mv mv
            LEFT JOIN erp_refs erp ON erp.ref_compact = UPPER(REPLACE(mv.normalized_filter, ' ', ''))
            WHERE EXISTS (
                    SELECT 1
                    FROM search_history_sync sh
                    WHERE sh.is_closed = false
                      AND sh.normalized_filter = mv.normalized_filter
                  )
              AND (:includeExistingInErp = true OR erp.ref_compact IS NULL)
            ORDER BY mv.priority_score DESC, mv.last_search_date DESC, mv.normalized_filter ASC
            LIMIT :limit OFFSET :offset
        )
        SELECT *
        FROM page_rows
        ORDER BY priorityScore DESC, lastSearchDate DESC, normalizedFilter ASC
        """,
        nativeQuery = true)
    List<Map<String, Object>> getSearchOpportunitiesFromCache(
            @Param("includeExistingInErp") boolean includeExistingInErp,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query(value = """
        WITH erp_refs AS (
            SELECT DISTINCT
                CASE
                    WHEN LOWER(COALESCE(e.produit, '')) IN ('true', '1', 't', 'yes', 'y')
                        THEN REPLACE(UPPER(REPLACE(e.no, ' ', '')), 'MASTER', '')
                    ELSE UPPER(REPLACE(e.no, ' ', ''))
                END AS ref_compact
            FROM elva_item_cache e
        )
        SELECT COUNT(*)
        FROM search_opportunity_mv mv
        LEFT JOIN erp_refs erp ON erp.ref_compact = UPPER(REPLACE(mv.normalized_filter, ' ', ''))
        WHERE EXISTS (
                SELECT 1
                FROM search_history_sync sh
                WHERE sh.is_closed = false
                  AND sh.normalized_filter = mv.normalized_filter
              )
          AND (:includeExistingInErp = true OR erp.ref_compact IS NULL)
        """,
        nativeQuery = true)
    long countSearchOpportunitiesFromCache(
            @Param("includeExistingInErp") boolean includeExistingInErp);

    @Query(value = """
        WITH candidate_filters AS (
            SELECT
                (sh.normalized_filter || '') AS normalized_filter,
                MIN(sh.filterdecoded) AS originalFilterExample,
                CAST(COUNT(*) AS INTEGER) AS totalAttempts,
                CAST(COUNT(DISTINCT COALESCE(CAST(sh.customer_id AS VARCHAR), sh.customer_ext_id, sh.companyname, 'UNKNOWN_CLIENT')) AS INTEGER) AS distinctCustomersCount,
                MIN(sh.creationdate) AS firstSearchDate,
                MAX(sh.creationdate) AS lastSearchDate,
                MAX(sh.id) AS latestSearchId,
                CAST(SUM(CASE WHEN sh.resultscount = 0 THEN 1 ELSE 0 END) AS INTEGER) AS zeroResultCount,
                CAST(SUM(CASE WHEN sh.isstockavailable = false OR sh.isstockavailable IS NULL THEN 1 ELSE 0 END) AS INTEGER) AS noStockCount,
                CAST(SUM(CASE WHEN sh.isstockavailable = true THEN 1 ELSE 0 END) AS INTEGER) AS withStockCount,
                CAST(
                    COUNT(DISTINCT COALESCE(CAST(sh.customer_id AS VARCHAR), sh.customer_ext_id, sh.companyname, 'UNKNOWN_CLIENT')) * 30
                    + COUNT(*) * 10
                    + SUM(CASE WHEN sh.resultscount = 0 THEN 1 ELSE 0 END) * 20
                    + SUM(CASE WHEN sh.isstockavailable = false OR sh.isstockavailable IS NULL THEN 1 ELSE 0 END) * 10
                    + CASE
                        WHEN MAX(sh.creationdate) >= CURRENT_TIMESTAMP - INTERVAL '7 days' THEN 30
                        WHEN MAX(sh.creationdate) >= CURRENT_TIMESTAMP - INTERVAL '30 days' THEN 15
                        WHEN MAX(sh.creationdate) >= CURRENT_TIMESTAMP - INTERVAL '90 days' THEN 5
                        ELSE 0
                      END
                AS BIGINT) AS priorityScore
            FROM search_history_sync sh
            WHERE sh.is_closed = false
              AND sh.normalized_filter IS NOT NULL
              AND sh.normalized_filter <> ''
              AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR sh.creationdate >= CAST(:fromDate AS TIMESTAMP))
              AND (CAST(:toDate AS TIMESTAMP) IS NULL OR sh.creationdate <= CAST(:toDate AS TIMESTAMP))
              AND (:customerId IS NULL OR sh.customer_id = :customerId)
              AND (:extId IS NULL OR sh.customer_ext_id = :extId OR sh.normalized_filter = :extId)
              AND (:companyName IS NULL OR sh.companyname ILIKE CONCAT('%', CAST(:companyName AS VARCHAR), '%'))
              AND (:type IS NULL OR sh.type = :type)
            GROUP BY (sh.normalized_filter || '')
        ),
        erp_refs AS (
            SELECT DISTINCT
                CASE
                    WHEN LOWER(COALESCE(e.produit, '')) IN ('true', '1', 't', 'yes', 'y')
                        THEN REPLACE(UPPER(REPLACE(e.no, ' ', '')), 'MASTER', '')
                    ELSE UPPER(REPLACE(e.no, ' ', ''))
                END AS ref_compact
            FROM elva_item_cache e
        ),
        page_filters AS (
            SELECT cf.*, COUNT(*) OVER() AS total_count
            FROM candidate_filters cf
            LEFT JOIN erp_refs erp ON erp.ref_compact = UPPER(REPLACE(cf.normalized_filter, ' ', ''))
            WHERE :includeExistingInErp = true OR erp.ref_compact IS NULL
            ORDER BY cf.priorityScore DESC, cf.lastSearchDate DESC, cf.normalized_filter ASC
            LIMIT :limit OFFSET :offset
        )
        SELECT
            pf.normalized_filter AS normalizedFilter,
            pf.originalFilterExample AS originalFilterExample,
            pf.totalAttempts AS totalAttempts,
            pf.distinctCustomersCount AS distinctCustomersCount,
            pf.firstSearchDate AS firstSearchDate,
            pf.lastSearchDate AS lastSearchDate,
            pf.latestSearchId AS latestSearchId,
            pf.zeroResultCount AS zeroResultCount,
            pf.noStockCount AS noStockCount,
            pf.withStockCount AS withStockCount,
            pf.total_count AS totalCount,
            pf.priorityScore AS priorityScore
        FROM page_filters pf
        ORDER BY pf.priorityScore DESC, pf.lastSearchDate DESC, pf.normalized_filter ASC
        """,
        nativeQuery = true)
    List<Map<String, Object>> getSearchOpportunitiesFast(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("customerId") Long customerId,
            @Param("extId") String extId,
            @Param("companyName") String companyName,
            @Param("type") String type,
            @Param("includeExistingInErp") boolean includeExistingInErp,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query(value = """
        WITH candidate_filters AS (
            SELECT sh.normalized_filter
            FROM search_history_sync sh
            WHERE sh.is_closed = false
              AND sh.normalized_filter IS NOT NULL
              AND sh.normalized_filter <> ''
              AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR sh.creationdate >= CAST(:fromDate AS TIMESTAMP))
              AND (CAST(:toDate AS TIMESTAMP) IS NULL OR sh.creationdate <= CAST(:toDate AS TIMESTAMP))
              AND (:customerId IS NULL OR sh.customer_id = :customerId)
              AND (:extId IS NULL OR sh.customer_ext_id = :extId OR sh.normalized_filter = :extId)
              AND (:companyName IS NULL OR sh.companyname ILIKE CONCAT('%', CAST(:companyName AS VARCHAR), '%'))
              AND (:type IS NULL OR sh.type = :type)
            GROUP BY sh.normalized_filter
        ),
        erp_refs AS (
            SELECT DISTINCT
                CASE
                    WHEN LOWER(COALESCE(e.produit, '')) IN ('true', '1', 't', 'yes', 'y')
                        THEN REPLACE(UPPER(REPLACE(e.no, ' ', '')), 'MASTER', '')
                    ELSE UPPER(REPLACE(e.no, ' ', ''))
                END AS ref_compact
            FROM elva_item_cache e
        )
        SELECT COUNT(*)
        FROM candidate_filters cf
        LEFT JOIN erp_refs erp ON erp.ref_compact = UPPER(REPLACE(cf.normalized_filter, ' ', ''))
        WHERE :includeExistingInErp = true OR erp.ref_compact IS NULL
        """,
        nativeQuery = true)
    long countSearchOpportunitiesFast(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("customerId") Long customerId,
            @Param("extId") String extId,
            @Param("companyName") String companyName,
            @Param("type") String type,
            @Param("includeExistingInErp") boolean includeExistingInErp);

    @Query(value = """
        WITH candidate_filters AS (
            SELECT
                (sh.normalized_filter || '') AS normalized_filter,
                MIN(sh.filterdecoded) AS originalFilterExample,
                CAST(COUNT(*) AS INTEGER) AS totalAttempts,
                CAST(COUNT(DISTINCT COALESCE(CAST(sh.customer_id AS VARCHAR), sh.customer_ext_id, sh.companyname, 'UNKNOWN_CLIENT')) AS INTEGER) AS distinctCustomersCount,
                MIN(sh.creationdate) AS firstSearchDate,
                MAX(sh.creationdate) AS lastSearchDate,
                MAX(sh.id) AS latestSearchId,
                CAST(SUM(CASE WHEN sh.resultscount = 0 THEN 1 ELSE 0 END) AS INTEGER) AS zeroResultCount,
                CAST(SUM(CASE WHEN sh.isstockavailable = false OR sh.isstockavailable IS NULL THEN 1 ELSE 0 END) AS INTEGER) AS noStockCount,
                CAST(SUM(CASE WHEN sh.isstockavailable = true THEN 1 ELSE 0 END) AS INTEGER) AS withStockCount,
                CAST(
                    COUNT(DISTINCT COALESCE(CAST(sh.customer_id AS VARCHAR), sh.customer_ext_id, sh.companyname, 'UNKNOWN_CLIENT')) * 30
                    + COUNT(*) * 10
                    + SUM(CASE WHEN sh.resultscount = 0 THEN 1 ELSE 0 END) * 20
                    + SUM(CASE WHEN sh.isstockavailable = false OR sh.isstockavailable IS NULL THEN 1 ELSE 0 END) * 10
                    + CASE
                        WHEN MAX(sh.creationdate) >= CURRENT_TIMESTAMP - INTERVAL '7 days' THEN 30
                        WHEN MAX(sh.creationdate) >= CURRENT_TIMESTAMP - INTERVAL '30 days' THEN 15
                        WHEN MAX(sh.creationdate) >= CURRENT_TIMESTAMP - INTERVAL '90 days' THEN 5
                        ELSE 0
                      END
                AS BIGINT) AS priorityScore
            FROM search_history_sync sh
            WHERE sh.is_closed = false
              AND sh.normalized_filter IS NOT NULL
              AND sh.normalized_filter <> ''
              AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR sh.creationdate >= CAST(:fromDate AS TIMESTAMP))
              AND (CAST(:toDate AS TIMESTAMP) IS NULL OR sh.creationdate <= CAST(:toDate AS TIMESTAMP))
              AND (:customerId IS NULL OR sh.customer_id = :customerId)
              AND (:extId IS NULL OR sh.customer_ext_id = :extId OR sh.normalized_filter = :extId)
              AND (:companyName IS NULL OR sh.companyname ILIKE CONCAT('%', CAST(:companyName AS VARCHAR), '%'))
              AND (:type IS NULL OR sh.type = :type)
            GROUP BY (sh.normalized_filter || '')
            HAVING (CAST(:onlyZeroResults AS BOOLEAN) IS NULL OR CAST(:onlyZeroResults AS BOOLEAN) = false OR SUM(CASE WHEN sh.resultscount = 0 THEN 1 ELSE 0 END) > 0)
               AND (CAST(:onlyNoStock AS BOOLEAN) IS NULL OR CAST(:onlyNoStock AS BOOLEAN) = false OR SUM(CASE WHEN sh.isstockavailable = false OR sh.isstockavailable IS NULL THEN 1 ELSE 0 END) > 0)
               AND (:minAttempts IS NULL OR COUNT(*) >= :minAttempts)
               AND (:minDistinctCustomers IS NULL OR COUNT(DISTINCT COALESCE(CAST(sh.customer_id AS VARCHAR), sh.customer_ext_id, sh.companyname, 'UNKNOWN_CLIENT')) >= :minDistinctCustomers)
        ),
        erp_refs AS (
            SELECT DISTINCT
                CASE
                    WHEN LOWER(COALESCE(e.produit, '')) IN ('true', '1', 't', 'yes', 'y')
                        THEN REPLACE(UPPER(REPLACE(e.no, ' ', '')), 'MASTER', '')
                    ELSE UPPER(REPLACE(e.no, ' ', ''))
                END AS ref_compact
            FROM elva_item_cache e
        ),
        page_filters AS (
            SELECT cf.*, COUNT(*) OVER() AS total_count
            FROM candidate_filters cf
            LEFT JOIN erp_refs erp ON erp.ref_compact = UPPER(REPLACE(cf.normalized_filter, ' ', ''))
            WHERE :includeExistingInErp = true OR erp.ref_compact IS NULL
            ORDER BY cf.priorityScore DESC, cf.lastSearchDate DESC, cf.normalized_filter ASC
            LIMIT :limit OFFSET :offset
        )
        SELECT
            pf.normalized_filter AS normalizedFilter,
            pf.originalFilterExample AS originalFilterExample,
            pf.totalAttempts AS totalAttempts,
            pf.distinctCustomersCount AS distinctCustomersCount,
            pf.firstSearchDate AS firstSearchDate,
            pf.lastSearchDate AS lastSearchDate,
            pf.latestSearchId AS latestSearchId,
            pf.zeroResultCount AS zeroResultCount,
            pf.noStockCount AS noStockCount,
            pf.withStockCount AS withStockCount,
            pf.total_count AS totalCount,
            pf.priorityScore AS priorityScore
        FROM page_filters pf
        ORDER BY pf.priorityScore DESC, pf.lastSearchDate DESC, pf.normalized_filter ASC
        """,
        nativeQuery = true)
    List<Map<String, Object>> getSearchOpportunitiesFiltered(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("customerId") Long customerId,
            @Param("extId") String extId,
            @Param("companyName") String companyName,
            @Param("type") String type,
            @Param("includeExistingInErp") boolean includeExistingInErp,
            @Param("onlyZeroResults") Boolean onlyZeroResults,
            @Param("onlyNoStock") Boolean onlyNoStock,
            @Param("minAttempts") Integer minAttempts,
            @Param("minDistinctCustomers") Integer minDistinctCustomers,
            @Param("limit") int limit,
            @Param("offset") long offset);

    @Query(value = """
        WITH candidate_filters AS (
            SELECT sh.normalized_filter
            FROM search_history_sync sh
            WHERE sh.is_closed = false
              AND sh.normalized_filter IS NOT NULL
              AND sh.normalized_filter <> ''
              AND (CAST(:fromDate AS TIMESTAMP) IS NULL OR sh.creationdate >= CAST(:fromDate AS TIMESTAMP))
              AND (CAST(:toDate AS TIMESTAMP) IS NULL OR sh.creationdate <= CAST(:toDate AS TIMESTAMP))
              AND (:customerId IS NULL OR sh.customer_id = :customerId)
              AND (:extId IS NULL OR sh.customer_ext_id = :extId OR sh.normalized_filter = :extId)
              AND (:companyName IS NULL OR sh.companyname ILIKE CONCAT('%', CAST(:companyName AS VARCHAR), '%'))
              AND (:type IS NULL OR sh.type = :type)
            GROUP BY sh.normalized_filter
            HAVING (CAST(:onlyZeroResults AS BOOLEAN) IS NULL OR CAST(:onlyZeroResults AS BOOLEAN) = false OR SUM(CASE WHEN sh.resultscount = 0 THEN 1 ELSE 0 END) > 0)
               AND (CAST(:onlyNoStock AS BOOLEAN) IS NULL OR CAST(:onlyNoStock AS BOOLEAN) = false OR SUM(CASE WHEN sh.isstockavailable = false OR sh.isstockavailable IS NULL THEN 1 ELSE 0 END) > 0)
               AND (:minAttempts IS NULL OR COUNT(*) >= :minAttempts)
               AND (:minDistinctCustomers IS NULL OR COUNT(DISTINCT COALESCE(CAST(sh.customer_id AS VARCHAR), sh.customer_ext_id, sh.companyname, 'UNKNOWN_CLIENT')) >= :minDistinctCustomers)
        ),
        erp_refs AS (
            SELECT DISTINCT
                CASE
                    WHEN LOWER(COALESCE(e.produit, '')) IN ('true', '1', 't', 'yes', 'y')
                        THEN REPLACE(UPPER(REPLACE(e.no, ' ', '')), 'MASTER', '')
                    ELSE UPPER(REPLACE(e.no, ' ', ''))
                END AS ref_compact
            FROM elva_item_cache e
        )
        SELECT COUNT(*)
        FROM candidate_filters cf
        LEFT JOIN erp_refs erp ON erp.ref_compact = UPPER(REPLACE(cf.normalized_filter, ' ', ''))
        WHERE :includeExistingInErp = true OR erp.ref_compact IS NULL
        """,
        nativeQuery = true)
    long countSearchOpportunitiesFiltered(
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            @Param("customerId") Long customerId,
            @Param("extId") String extId,
            @Param("companyName") String companyName,
            @Param("type") String type,
            @Param("includeExistingInErp") boolean includeExistingInErp,
            @Param("onlyZeroResults") Boolean onlyZeroResults,
            @Param("onlyNoStock") Boolean onlyNoStock,
            @Param("minAttempts") Integer minAttempts,
            @Param("minDistinctCustomers") Integer minDistinctCustomers);

    @Query("SELECT l FROM LocalSearchHistory l WHERE l.normalizedFilter = :normalizedFilter AND l.isClosed = false")
    List<LocalSearchHistory> findActiveByNormalizedFilter(@Param("normalizedFilter") String normalizedFilter);

    @Modifying
    @Query("""
        UPDATE LocalSearchHistory l
        SET l.isClosed = true,
            l.closedAt = :closedAt,
            l.closedBy = :closedBy,
            l.closureReason = :closureReason,
            l.diagnosticStatus = :diagnosticStatus,
            l.diagnosticComment = :diagnosticComment,
            l.actionType = :actionType,
            l.linkedArticleId = :linkedArticleId,
            l.closedBatchId = :closedBatchId
        WHERE l.normalizedFilter = :normalizedFilter
          AND l.isClosed = false
        """)
    int closeOpportunities(
            @Param("normalizedFilter") String normalizedFilter,
            @Param("closedAt") LocalDateTime closedAt,
            @Param("closedBy") String closedBy,
            @Param("closureReason") String closureReason,
            @Param("diagnosticStatus") String diagnosticStatus,
            @Param("diagnosticComment") String diagnosticComment,
            @Param("actionType") String actionType,
            @Param("linkedArticleId") Long linkedArticleId,
            @Param("closedBatchId") UUID closedBatchId
    );

    // --- STATS ---
    @Query(value = """
        SELECT
            CAST(COUNT(*) AS BIGINT) AS totalUnclosedSearchLines,
            CAST(SUM(CASE WHEN resultscount = 0 THEN 1 ELSE 0 END) AS BIGINT) AS totalZeroResultSearches,
            CAST(SUM(CASE WHEN isstockavailable = false OR isstockavailable IS NULL THEN 1 ELSE 0 END) AS BIGINT) AS totalNoStockSearches
        FROM search_history_sync
        WHERE is_closed = false
        """,
        nativeQuery = true)
    Map<String, Object> getBasicStats();

    @Query("SELECT count(l) FROM LocalSearchHistory l WHERE l.isClosed = false")
    long countTotalUnclosedSearchLines();

    @Query("SELECT count(DISTINCT l.normalizedFilter) FROM LocalSearchHistory l WHERE l.isClosed = false AND l.normalizedFilter IS NOT NULL")
    long countTotalUnclosedOpportunities();

    @Query("SELECT count(l) FROM LocalSearchHistory l WHERE l.isClosed = false AND l.resultsCount = 0")
    long countTotalZeroResultSearches();

    @Query("SELECT count(l) FROM LocalSearchHistory l WHERE l.isClosed = false AND (l.isStockAvailable = false OR l.isStockAvailable IS NULL)")
    long countTotalNoStockSearches();

    boolean existsByDiagnosticStatus(String diagnosticStatus);
    boolean existsByActionType(String actionType);
}
