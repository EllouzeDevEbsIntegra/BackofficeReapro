package com.reapro.achat.DTO;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SearchOpportunityStatsResponse {
    private long totalUnclosedSearchLines;
    private long totalUnclosedOpportunities;
    private long totalZeroResultSearches;
    private long totalNoStockSearches;
}
