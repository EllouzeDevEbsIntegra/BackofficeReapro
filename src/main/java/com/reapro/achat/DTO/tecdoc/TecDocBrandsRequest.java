package com.reapro.achat.DTO.tecdoc;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TecDocBrandsRequest {

    private GetBrandsParams getBrands;

    @Data
    @Builder
    public static class GetBrandsParams {
        private String articleCountry;
        private Long provider;
        private String lang;
        private Integer dataSupplierIds;
        private Boolean includeDataSupplierLogo;
    }
}