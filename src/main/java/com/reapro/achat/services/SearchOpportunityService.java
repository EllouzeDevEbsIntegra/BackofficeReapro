package com.reapro.achat.services;

import com.reapro.achat.DTO.CloseSearchOpportunityRequest;
import com.reapro.achat.DTO.SearchOpportunityCustomerResponse;
import com.reapro.achat.DTO.SearchOpportunityResponse;
import com.reapro.achat.DTO.SearchOpportunityStatsResponse;
import com.reapro.achat.entities.primary.Exclusion;
import com.reapro.achat.entities.primary.LocalSearchHistory;
import com.reapro.achat.entities.primary.SearchOpportunityDecision;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import com.reapro.achat.repositories.primary.ExclusionRepository;
import com.reapro.achat.repositories.primary.LocalSearchHistoryRepository;
import com.reapro.achat.repositories.primary.SearchOpportunityDecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchOpportunityService {

    private final LocalSearchHistoryRepository localSearchHistoryRepository;
    private final SearchOpportunityDecisionRepository searchOpportunityDecisionRepository;
    private final ExclusionRepository exclusionRepository;
    private final SearchOpportunityCacheService searchOpportunityCacheService;
    @Qualifier("sqlServerJdbcTemplate")
    private final NamedParameterJdbcTemplate sqlServerJdbcTemplate;
    @Value("${search.opportunity.live-erp-check.enabled:false}")
    private boolean liveErpCheckEnabled;

    private LocalDateTime toLocalDateTime(Object raw) {
        if (raw == null) return null;
        if (raw instanceof LocalDateTime ldt) return ldt;
        if (raw instanceof Timestamp ts) return ts.toLocalDateTime();
        return LocalDateTime.parse(raw.toString());
    }

    @Transactional(readOnly = true)
    public Page<SearchOpportunityResponse> getSearchOpportunities(
            Pageable pageable,
            LocalDateTime fromDate,
            LocalDateTime toDate,
            Long customerId,
            String extId,
            String companyName,
            String type,
            Boolean onlyZeroResults,
            Boolean onlyNoStock,
            Integer minAttempts,
            Integer minDistinctCustomers,
            Boolean includeExistingInErp) {

        boolean onlyZeroResultsFlag = Boolean.TRUE.equals(onlyZeroResults);
        boolean onlyNoStockFlag = Boolean.TRUE.equals(onlyNoStock);
        boolean includeExistingInErpFlag = Boolean.TRUE.equals(includeExistingInErp);
        boolean requireAggregateFilters = onlyZeroResultsFlag
                || onlyNoStockFlag
                || minAttempts != null
                || minDistinctCustomers != null;
        boolean hasBaseFilters = fromDate != null
                || toDate != null
                || customerId != null
                || hasText(extId)
                || hasText(companyName)
                || hasText(type);
        boolean cacheEligible = !requireAggregateFilters && !hasBaseFilters;
        int requestedSize = pageable.getPageSize();
        int queryLimit = requestedSize;
        if (!includeExistingInErpFlag && cacheEligible && liveErpCheckEnabled) {
            queryLimit = Math.min(requestedSize * 3, 200);
        }

        List<Map<String, Object>> baseResults;
        boolean usedCache = false;

        if (cacheEligible) {
            try {
                baseResults = localSearchHistoryRepository
                        .getSearchOpportunitiesFromCache(
                                includeExistingInErpFlag,
                                queryLimit,
                                pageable.getOffset());
                usedCache = true;
            } catch (Exception ex) {
                log.warn("Cache query failed, fallback to base fast query", ex);
                baseResults = localSearchHistoryRepository
                        .getSearchOpportunitiesFast(
                                fromDate, toDate, customerId, extId, companyName, type,
                                includeExistingInErpFlag,
                                queryLimit,
                                pageable.getOffset());
            }

        } else if (requireAggregateFilters) {
            baseResults = localSearchHistoryRepository
                    .getSearchOpportunitiesFiltered(
                            fromDate, toDate, customerId, extId, companyName, type,
                            includeExistingInErpFlag,
                            onlyZeroResultsFlag,
                            onlyNoStockFlag,
                            minAttempts, minDistinctCustomers,
                            queryLimit,
                            pageable.getOffset());

        } else {
            baseResults = localSearchHistoryRepository
                    .getSearchOpportunitiesFast(
                            fromDate, toDate, customerId, extId, companyName, type,
                            includeExistingInErpFlag,
                            queryLimit,
                            pageable.getOffset());

        }

        long totalElements;
        if (baseResults.isEmpty()) {
            if (usedCache) {
                totalElements = localSearchHistoryRepository.countSearchOpportunitiesFromCache(includeExistingInErpFlag);
            } else if (requireAggregateFilters) {
                totalElements = localSearchHistoryRepository.countSearchOpportunitiesFiltered(
                        fromDate, toDate, customerId, extId, companyName, type,
                        includeExistingInErpFlag,
                        onlyZeroResultsFlag,
                        onlyNoStockFlag,
                        minAttempts,
                        minDistinctCustomers);
            } else {
                totalElements = localSearchHistoryRepository.countSearchOpportunitiesFast(
                        fromDate, toDate, customerId, extId, companyName, type,
                        includeExistingInErpFlag);
            }
        } else {
            totalElements = ((Number) baseResults.get(0).get("totalcount")).longValue();
        }

        List<SearchOpportunityResponse> content = baseResults.stream().map(row -> {
            String normalizedFilter = (String) row.get("normalizedfilter");
            String originalFilterExample = (String) row.get("originalfilterexample");
            int totalAttempts = ((Number) row.get("totalattempts")).intValue();
            int distinctCustomersCount = ((Number) row.get("distinctcustomerscount")).intValue();
            LocalDateTime firstSearchDate = toLocalDateTime(row.get("firstsearchdate"));
            LocalDateTime lastSearchDate = toLocalDateTime(row.get("lastsearchdate"));
            long latestSearchId = ((Number) row.get("latestsearchid")).longValue();
            int zeroResultCount = ((Number) row.get("zeroresultcount")).intValue();
            int noStockCount = ((Number) row.get("nostockcount")).intValue();
            int withStockCount = ((Number) row.get("withstockcount")).intValue();

            long priorityScore = row.get("priorityscore") != null
                    ? ((Number) row.get("priorityscore")).longValue()
                    : calculatePriorityScore(distinctCustomersCount, totalAttempts, zeroResultCount, noStockCount, lastSearchDate);

            List<SearchOpportunityCustomerResponse> customers = new ArrayList<>();

            return SearchOpportunityResponse.builder()
                    .normalizedFilter(normalizedFilter)
                    .originalFilterExample(originalFilterExample)
                    .totalAttempts(totalAttempts)
                    .distinctCustomersCount(distinctCustomersCount)
                    .firstSearchDate(firstSearchDate)
                    .lastSearchDate(lastSearchDate)
                    .latestSearchId(latestSearchId)
                    .zeroResultCount(zeroResultCount)
                    .noStockCount(noStockCount)
                    .withStockCount(withStockCount)
                    .priorityScore(priorityScore)
                    .customers(customers)
                    .build();
        }).collect(Collectors.toList());

        if (!includeExistingInErpFlag && liveErpCheckEnabled && !content.isEmpty()) {
            content = filterOutRefsExistingInErpLive(content);
        }
        if (content.size() > requestedSize) {
            content = new ArrayList<>(content.subList(0, requestedSize));
        }

        return new PageImpl<>(content, pageable, totalElements);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<SearchOpportunityResponse> filterOutRefsExistingInErpLive(List<SearchOpportunityResponse> input) {
        Set<String> refs = input.stream()
                .map(SearchOpportunityResponse::getNormalizedFilter)
                .map(this::normalizeRefForComparison)
                .filter(this::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (refs.isEmpty()) {
            return input;
        }

        try {
            // Fast path: probe only candidate keys on No_ (index-friendly), then apply MASTER rule in Java.
            Set<String> candidateNos = new LinkedHashSet<>();
            for (String ref : refs) {
                candidateNos.add(ref);
                candidateNos.add("MASTER" + ref);
            }

            String sql = """
                    SELECT DISTINCT
                        No_ AS no_raw,
                        Produit AS produit_raw
                    FROM ELVA_Item
                    WHERE No_ IN (:candidateNos)
                    """;
            MapSqlParameterSource params = new MapSqlParameterSource("candidateNos", candidateNos);

            Set<String> existingRefs = sqlServerJdbcTemplate.query(sql, params, rs -> {
                Set<String> result = new HashSet<>();
                while (rs.next()) {
                    String noRaw = normalizeRefForComparison(rs.getString("no_raw"));
                    if (!hasText(noRaw)) {
                        continue;
                    }
                    String produitRaw = rs.getString("produit_raw");
                    if (isProduitTrue(produitRaw)) {
                        result.add(noRaw.replace("MASTER", ""));
                    } else {
                        result.add(noRaw);
                    }
                }
                return result;
            });

            if (existingRefs.isEmpty()) {
                return input;
            }

            return input.stream()
                    .filter(row -> !existingRefs.contains(normalizeRefForComparison(row.getNormalizedFilter())))
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            log.warn("Live ERP existence check failed. Keeping cache-based filtering only.", ex);
            return input;
        }
    }

    private boolean isProduitTrue(String produitValue) {
        if (!hasText(produitValue)) {
            return false;
        }
        String v = produitValue.trim().toLowerCase(Locale.ROOT);
        return "true".equals(v) || "1".equals(v) || "t".equals(v) || "yes".equals(v) || "y".equals(v);
    }

    private String normalizeRefForComparison(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.replace(" ", "").toUpperCase(Locale.ROOT);
    }

    private long calculatePriorityScore(int distinctCustomersCount, int totalAttempts, int zeroResultCount, int noStockCount, LocalDateTime lastSearchDate) {
        long score = (distinctCustomersCount * 30L)
                + (totalAttempts * 10L)
                + (zeroResultCount * 20L)
                + (noStockCount * 10L);

        if (lastSearchDate != null) {
            long daysSinceLastSearch = ChronoUnit.DAYS.between(lastSearchDate, LocalDateTime.now());
            if (daysSinceLastSearch <= 7) {
                score += 30;
            } else if (daysSinceLastSearch <= 30) {
                score += 15;
            } else if (daysSinceLastSearch <= 90) {
                score += 5;
            }
        }
        return score;
    }

    @Transactional(readOnly = true)
    public List<LocalSearchHistory> getOpportunityDetails(String normalizedFilter) {
        if (normalizedFilter == null || normalizedFilter.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "normalizedFilter ne peut pas ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Âªtre vide");
        }
        return localSearchHistoryRepository.findActiveByNormalizedFilter(normalizedFilter);
    }

    @Transactional
    public int closeOpportunity(String email, String normalizedFilter, CloseSearchOpportunityRequest request) {
        if (normalizedFilter == null || normalizedFilter.isBlank()) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "normalizedFilter ne peut pas ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Âªtre vide");
        }

        List<LocalSearchHistory> activeLines = localSearchHistoryRepository.findActiveByNormalizedFilter(normalizedFilter);

        if (activeLines.isEmpty()) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Aucune recherche active trouvÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©e pour ce filtre normalisÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â© : " + normalizedFilter);
        }

        int totalAttempts = activeLines.size();
        Set<String> distinctClients = new HashSet<>();
        LocalDateTime firstSearchDate = null;
        LocalDateTime lastSearchDate = null;
        int zeroResultCount = 0;
        int noStockCount = 0;
        String originalFilterExample = activeLines.get(0).getFilterDecoded();

        for (LocalSearchHistory line : activeLines) {
            String clientKey = line.getCustomerId() != null ? String.valueOf(line.getCustomerId()) :
                               line.getCustomerExtId() != null ? line.getCustomerExtId() :
                               line.getCompanyName() != null ? line.getCompanyName() : "UNKNOWN_CLIENT";
            distinctClients.add(clientKey);

            if (firstSearchDate == null || (line.getCreationDate() != null && line.getCreationDate().isBefore(firstSearchDate))) {
                firstSearchDate = line.getCreationDate();
            }
            if (lastSearchDate == null || (line.getCreationDate() != null && line.getCreationDate().isAfter(lastSearchDate))) {
                lastSearchDate = line.getCreationDate();
            }

            if (line.getResultsCount() != null && line.getResultsCount() == 0) {
                zeroResultCount++;
            }
            if (line.getIsStockAvailable() == null || !line.getIsStockAvailable()) {
                noStockCount++;
            }
        }

        UUID closedBatchId = UUID.randomUUID();
        LocalDateTime closedAt = LocalDateTime.now();

        int updatedRows = localSearchHistoryRepository.closeOpportunities(
                normalizedFilter,
                closedAt,
                email, 
                request.getClosureReason(),
                request.getDiagnosticStatus(),
                request.getComment(),
                request.getActionType(),
                request.getLinkedArticleId(),
                closedBatchId
        );

        SearchOpportunityDecision decision = SearchOpportunityDecision.builder()
                .normalizedFilter(normalizedFilter)
                .originalFilterExample(originalFilterExample)
                .decisionDate(closedAt)
                .decidedBy(email)
                .diagnosticStatus(request.getDiagnosticStatus())
                .actionType(request.getActionType())
                .closureReason(request.getClosureReason())
                .comment(request.getComment())
                .linkedArticleId(request.getLinkedArticleId())
                .closedBatchId(closedBatchId)
                .closedRowsCount(updatedRows)
                .firstSearchDate(firstSearchDate)
                .lastSearchDate(lastSearchDate)
                .distinctCustomersCount(distinctClients.size())
                .totalAttempts(totalAttempts)
                .zeroResultCount(zeroResultCount)
                .noStockCount(noStockCount)
                .build();

        searchOpportunityDecisionRepository.save(decision);

        // NOUVEAU: GÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â©rer l'exclusion pour le futur
        if (request.isExcludeFromFutureSync()) {
            if (!exclusionRepository.existsByNormalizedFilter(normalizedFilter)) {
                Exclusion exclusion = Exclusion.builder()
                        .normalizedFilter(normalizedFilter)
                        .reason(request.getClosureReason())
                        .createdBy(email)
                        .build();
                exclusionRepository.save(exclusion);
                log.info("Filter '{}' added to exclusions list.", normalizedFilter);
            }
        }

        log.info("Opportunity '{}' closed by {}. Rows affected: {}", normalizedFilter, email, updatedRows);
        if (updatedRows > 0) {
            searchOpportunityCacheService.refreshAsync();
        }

        return updatedRows;
    }

    @Transactional(readOnly = true)
    public SearchOpportunityStatsResponse getStats() {
        Map<String, Object> basicStats = localSearchHistoryRepository.getBasicStats();
        
        long totalUnclosedSearchLines = 0L;
        long totalZeroResultSearches = 0L;
        long totalNoStockSearches = 0L;
        
        if (basicStats != null) {
            totalUnclosedSearchLines = basicStats.get("totalunclosedsearchlines") != null 
                    ? ((Number) basicStats.get("totalunclosedsearchlines")).longValue() : 0L;
            totalZeroResultSearches = basicStats.get("totalzeroresultsearches") != null 
                    ? ((Number) basicStats.get("totalzeroresultsearches")).longValue() : 0L;
            totalNoStockSearches = basicStats.get("totalnostocksearches") != null 
                    ? ((Number) basicStats.get("totalnostocksearches")).longValue() : 0L;
        }
        
        long totalUnclosedOpportunities = localSearchHistoryRepository.countTotalUnclosedOpportunities();
        
        return SearchOpportunityStatsResponse.builder()
                .totalUnclosedSearchLines(totalUnclosedSearchLines)
                .totalUnclosedOpportunities(totalUnclosedOpportunities)
                .totalZeroResultSearches(totalZeroResultSearches)
                .totalNoStockSearches(totalNoStockSearches)
                .build();
    }
}

