package com.reapro.achat.DTO;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class SearchOpportunityResponse {
    private String normalizedFilter;
    private String originalFilterExample;
    private int totalAttempts;
    private int distinctCustomersCount;
    private List<SearchOpportunityCustomerResponse> customers;
    private LocalDateTime firstSearchDate;
    private LocalDateTime lastSearchDate;
    private int zeroResultCount;
    private int noStockCount;
    private int withStockCount;
    private long priorityScore;
    private long latestSearchId;
}
