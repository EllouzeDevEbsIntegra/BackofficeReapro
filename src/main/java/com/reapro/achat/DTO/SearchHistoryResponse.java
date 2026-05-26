package com.reapro.achat.DTO;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SearchHistoryResponse {
    private Long id;
    private String filterDecoded;
    private LocalDateTime creationDate;
    private String type;
    private String customerExtId;
    private String companyName;
    private Integer resultsCount;
    private Boolean isStockAvailable; // NOUVEAU CHAMP
    private LocalDateTime syncedAt;
}
