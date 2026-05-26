package com.reapro.achat.services;

import com.reapro.achat.entities.primary.LocalSearchHistory;
import com.reapro.achat.repositories.b2bnav.B2bSearchHistoryRepository;
import com.reapro.achat.repositories.primary.LocalSearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchHistorySyncService {

    private final B2bSearchHistoryRepository b2bSearchHistoryRepository;
    private final LocalSearchHistoryRepository localSearchHistoryRepository;
    private final ParameterService parameterService;
    private final ExclusionService exclusionService; // NOUVEAU
    private final SearchOpportunityCacheService searchOpportunityCacheService;

    private static final String LAST_SYNCED_ID_KEY = "LAST_SYNCED_SEARCH_HISTORY_ID";
    private static final int BATCH_SIZE = 1000;
    private final AtomicBoolean isSyncRunning = new AtomicBoolean(false);

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        new Thread(this::syncNewRecords).start();
    }

    @Scheduled(fixedRate = 900000)
    public void scheduledSync() {
        syncNewRecords();
    }

    @Transactional
    public void syncNewRecords() {
        if (isSyncRunning.getAndSet(true)) {
            log.info("Synchronisation dÃ©jÃ  en cours, saut de cette exÃ©cution.");
            return;
        }
        try {
            log.info("DÃ©marrage de la synchronisation de l'historique de recherche...");
            long lastId = Long.parseLong(parameterService.getValue(LAST_SYNCED_ID_KEY, "0"));
            int recordsProcessed = 0;
            
            // 1. Charger la liste d'exclusion en mÃ©moire AVANT la boucle
            Set<String> exclusionList = exclusionService.getExclusionList();

            while (true) {
                List<B2bSearchHistoryRepository.B2bSearchHistoryProjection> newRecords = b2bSearchHistoryRepository.findNewRecordsAfterId(lastId, BATCH_SIZE);
                if (newRecords.isEmpty()) {
                    log.info("Fin de la synchronisation. {} enregistrements traitÃ©s au total.", recordsProcessed);
                    if (recordsProcessed > 0) {
                        searchOpportunityCacheService.refreshAsync();
                    }
                    break;
                }
                
                // 2. Filtrer et mapper
                List<LocalSearchHistory> localEntities = newRecords.stream()
                        .map(projection -> mapProjectionToLocalEntity(projection, exclusionList))
                        .filter(java.util.Objects::nonNull) // Ignorer les lignes retournÃ©es null (exclues)
                        .collect(Collectors.toList());
                        
                localSearchHistoryRepository.saveAll(localEntities);
                
                long maxIdInBatch = newRecords.get(newRecords.size() - 1).getId();
                lastId = maxIdInBatch;
                parameterService.updateValue(LAST_SYNCED_ID_KEY, String.valueOf(lastId));
                recordsProcessed += newRecords.size();
                log.info("Lot de {} enregistrements synchronisÃ©. Dernier ID traitÃ© : {}", newRecords.size(), lastId);
            }
        } finally {
            isSyncRunning.set(false);
        }
    }

    private LocalSearchHistory mapProjectionToLocalEntity(B2bSearchHistoryRepository.B2bSearchHistoryProjection projection, Set<String> exclusionList) {
        String filterDecoded = projection.getFilterDecoded();
        String normalizedFilter = normalizeSearchFilter(filterDecoded);
        
        // 3. Ignorer si dans la liste d'exclusion
        if (normalizedFilter != null && exclusionList.contains(normalizedFilter)) {
            return null;
        }

        return LocalSearchHistory.builder()
                .id(projection.getId())
                .filterDecoded(filterDecoded)
                .normalizedFilter(normalizedFilter)
                .creationDate(projection.getCreationDate())
                .type(projection.getType())
                .customerId(projection.getCustomerId())
                .customerExtId(projection.getExtId())
                .companyName(projection.getCompanyName())
                .resultsCount(projection.getResultsCount())
                .isStockAvailable(projection.getIsStockAvailable())
                .isClosed(false)
                .syncedAt(LocalDateTime.now())
                .build();
    }

    public String normalizeSearchFilter(String value) {
        if (value == null) return null;
        String normalized = value
            .replaceAll("\\p{C}", "")
            .replaceAll("[\\[\\]*]", "") // Supprime les crochets et les astÃ©risques
            .replaceAll("^\\s*-\\s*", "")
            .replaceAll("Â ", " ")
            .trim()
            .toUpperCase(Locale.ROOT)
            .replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized;
    }

    @Transactional
    public int renormalizeExistingData() {
        log.info("DÃ©but de la renormalisation des anciennes donnÃ©es par lots...");
        int totalUpdatedCount = 0;
        Page<LocalSearchHistory> page;
        int pageNum = 0;
        final int BATCH_SIZE_RENORM = 5000;

        do {
            page = localSearchHistoryRepository.findAll(PageRequest.of(pageNum, BATCH_SIZE_RENORM));
            List<LocalSearchHistory> batchToUpdate = new ArrayList<>();
            
            for (LocalSearchHistory history : page.getContent()) {
                String newNormalized = normalizeSearchFilter(history.getFilterDecoded());
                if (newNormalized != null && !newNormalized.equals(history.getNormalizedFilter())) {
                    history.setNormalizedFilter(newNormalized);
                    batchToUpdate.add(history);
                }
            }
            
            if (!batchToUpdate.isEmpty()) {
                localSearchHistoryRepository.saveAll(batchToUpdate);
                totalUpdatedCount += batchToUpdate.size();
                log.info("Lot {} traitÃ©. {} lignes mises Ã  jour jusqu'Ã  prÃ©sent.", pageNum + 1, totalUpdatedCount);
            }
            
            pageNum++;
        } while (page.hasNext());
        
        log.info("Renormalisation terminÃ©e. {} lignes mises Ã  jour au total.", totalUpdatedCount);
        return totalUpdatedCount;
    }
}

