package com.reapro.achat.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    // Caches existants
    public static final String LEDGER_PAGE         = "itemLedgerEntriesPage";
    public static final String CACHE_BC_PARAMS     = "bcParameters";

    // ✅ Nouveau cache pour le recap (sum par EntryType)
    public static final String LEDGER_RECAP        = "itemLedgerEntriesRecap";

    public static final String CACHE_BC_MANUFACTURERS = "bcManufacturers";
    
    // ✅ Nouveau cache pour la structure des kits
    public static final String KITS_STRUCTURE      = "kitsStructure";

    // ✅ Nouveau cache pour les exclusions de recherche
    public static final String EXCLUSION_LIST      = "exclusionList";

    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager manager = new SimpleCacheManager();


        // Page ledger: TTL court
        CaffeineCache ledgerPage = new CaffeineCache(
                LEDGER_PAGE,
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(2000)
                        .build()
        );

        // ✅ Recap ledger: TTL court aussi (ou 5 minutes si tu préfères)
        CaffeineCache ledgerRecap = new CaffeineCache(
                LEDGER_RECAP,
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(2000)
                        .build()
        );

        CaffeineCache bcParameters = new CaffeineCache(
                CACHE_BC_PARAMS,
                Caffeine.newBuilder()
                        .expireAfterWrite(30, TimeUnit.MINUTES)
                        .maximumSize(500)
                        .build()
        );

        CaffeineCache bcManufacturers = new CaffeineCache(
                CACHE_BC_MANUFACTURERS,
                Caffeine.newBuilder()
                        .expireAfterWrite(60, TimeUnit.MINUTES)
                        .maximumSize(100) // Une entrée par companyId
                        .build()
        );
        
        // Cache pour la structure des kits (TTL long car change rarement)
        CaffeineCache kitsStructure = new CaffeineCache(
                KITS_STRUCTURE,
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS) // 24h de cache
                        .maximumSize(5000)
                        .build()
        );

        // Cache pour les exclusions (TTL très long, vidé manuellement lors d'un ajout/suppression)
        CaffeineCache exclusionList = new CaffeineCache(
                EXCLUSION_LIST,
                Caffeine.newBuilder()
                        .expireAfterWrite(24, TimeUnit.HOURS)
                        .maximumSize(10) // Contient un seul gros Set
                        .build()
        );

        manager.setCaches(List.of(ledgerPage, ledgerRecap, bcParameters, bcManufacturers, kitsStructure, exclusionList));
        return manager;
    }
}
