package com.reapro.achat.DTO.tecdoc;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TecDocSearchRequest {
    private GetArticlesParams getArticles;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GetArticlesParams {
        private String articleCountry;
        private Long provider;
        private String searchQuery;
        private Integer searchType;
        private Integer dataSupplierIds;
        private String lang;
        private Boolean includeAll;
    }
}