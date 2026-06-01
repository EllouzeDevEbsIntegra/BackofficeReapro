package com.reapro.achat.config.datasource;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PostgresIndexInitializer implements ApplicationRunner {

    @Qualifier("jdbcTemplate")
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            log.info("Checking and initializing PostgreSQL extensions and trigram indexes...");

            // 1. Activer l'extension pg_trgm pour la recherche trigramme
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            log.info("PostgreSQL pg_trgm extension verified/created.");

            // 2. CrÃ©er l'index GIN trigramme sur la colonne 'no'
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_elva_item_cache_no_trgm ON elva_item_cache USING gin (no gin_trgm_ops)");
            log.info("GIN trigram index on 'no' verified/created.");

            // 3. CrÃ©er l'index GIN trigramme sur la colonne 'search_description'
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_elva_item_cache_search_desc_trgm ON elva_item_cache USING gin (search_description gin_trgm_ops)");
            log.info("GIN trigram index on 'search_description' verified/created.");

            // 4. Index d'expression pour la comparaison ERP (normalisation + suppression MASTER si produit=true)
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_elva_item_cache_ref_compact_erp ON elva_item_cache ((CASE WHEN LOWER(COALESCE(produit, '')) IN ('true','1','t','yes','y') THEN REPLACE(UPPER(REPLACE(no, ' ', '')), 'MASTER', '') ELSE UPPER(REPLACE(no, ' ', '')) END))");
            log.info("Expression index for ERP compact reference verified/created.");

            // 5. Vue matÃ©rialisÃ©e des opportunitÃ©s (tri score global sans recalcul Ã  chaque requÃªte)
            jdbcTemplate.execute("""
                    CREATE MATERIALIZED VIEW IF NOT EXISTS search_opportunity_mv AS
                    SELECT
                        sh.normalized_filter,
                        MIN(sh.filterdecoded) AS original_filter_example,
                        CAST(COUNT(*) AS INTEGER) AS total_attempts,
                        CAST(COUNT(DISTINCT COALESCE(CAST(sh.customer_id AS VARCHAR), sh.customer_ext_id, sh.companyname, 'UNKNOWN_CLIENT')) AS INTEGER) AS distinct_customers_count,
                        MIN(sh.creationdate) AS first_search_date,
                        MAX(sh.creationdate) AS last_search_date,
                        MAX(sh.id) AS latest_search_id,
                        CAST(SUM(CASE WHEN sh.resultscount = 0 THEN 1 ELSE 0 END) AS INTEGER) AS zero_result_count,
                        CAST(SUM(CASE WHEN sh.isstockavailable = false OR sh.isstockavailable IS NULL THEN 1 ELSE 0 END) AS INTEGER) AS no_stock_count,
                        CAST(SUM(CASE WHEN sh.isstockavailable = true THEN 1 ELSE 0 END) AS INTEGER) AS with_stock_count,
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
                        AS BIGINT) AS priority_score
                    FROM search_history_sync sh
                    WHERE sh.is_closed = false
                      AND sh.normalized_filter IS NOT NULL
                      AND sh.normalized_filter <> ''
                    GROUP BY sh.normalized_filter
                    """);
            jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_search_opportunity_mv_normalized_filter ON search_opportunity_mv (normalized_filter)");
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_search_opportunity_mv_priority ON search_opportunity_mv (priority_score DESC, last_search_date DESC, normalized_filter ASC)");
            log.info("Materialized view search_opportunity_mv verified/created.");

            log.info("PostgreSQL indexing initialization completed successfully.");
        } catch (Exception e) {
            log.error("Failed to initialize PostgreSQL trigram indexes", e);
        }
    }
}

