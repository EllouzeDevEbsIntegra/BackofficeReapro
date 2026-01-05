package com.reapro.achat.DTO.tecdoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TecDocArticle {
    private Long dataSupplierId;
    private String articleNumber;
    private Long mfrId;
    private String mfrName;
    private String supplierLogoUrl;
    private List<TecDocGenericArticle> genericArticles;
    private List<String> gtins;
    private List<TecDocOemNumber> oemNumbers;
    private List<TecDocImage> images;
    private List<TecDocPdf> pdfs;
    private List<TecDocCriteria> articleCriteria;

    // Nouveau champ pour stocker les constructeurs de véhicules liés
    private List<VehicleManufacturer> linkedVehicles;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VehicleManufacturer {
        private Long manuId;
        private String manuName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TecDocGenericArticle {
        private Long genericArticleId;
        private String genericArticleDescription;
        // Champ indispensable pour l'appel à la seconde API
        private Long legacyArticleId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TecDocOemNumber {
        private String articleNumber;
        private String mfrName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TecDocImage {
        private String imageURL800;
        private String imageURL200;
        private String fileName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TecDocPdf {
        private String url;
        private String fileName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TecDocCriteria {
        private String criteriaDescription;
        private String formattedValue;
    }
}