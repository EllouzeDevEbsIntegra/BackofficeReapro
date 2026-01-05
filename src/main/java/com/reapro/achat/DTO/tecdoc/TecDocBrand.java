package com.reapro.achat.DTO.tecdoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TecDocBrand {

    private Long dataSupplierId;
    private String mfrName;
    private DataSupplierLogo dataSupplierLogo;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataSupplierLogo {
        private String imageURL100;
        private String imageURL200;
        private String imageURL400;
        private String imageURL800;
    }
}