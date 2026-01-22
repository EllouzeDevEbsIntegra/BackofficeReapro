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

            // 2. Créer l'index GIN trigramme sur la colonne 'no'
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_elva_item_cache_no_trgm ON elva_item_cache USING gin (no gin_trgm_ops)");
            log.info("GIN trigram index on 'no' verified/created.");

            // 3. Créer l'index GIN trigramme sur la colonne 'search_description'
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_elva_item_cache_search_desc_trgm ON elva_item_cache USING gin (search_description gin_trgm_ops)");
            log.info("GIN trigram index on 'search_description' verified/created.");

            log.info("PostgreSQL indexing initialization completed successfully.");
        } catch (Exception e) {
            log.error("Failed to initialize PostgreSQL trigram indexes", e);
        }
    }
}
