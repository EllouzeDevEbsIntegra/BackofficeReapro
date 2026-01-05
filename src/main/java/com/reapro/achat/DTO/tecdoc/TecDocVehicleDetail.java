package com.reapro.achat.DTO.tecdoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TecDocVehicleDetail {
    private Long carId;
    private String carDesc;
    private String manuDesc;
    private String modelDesc;
    private String constructionType;
    private Integer cylinderCapacity;
    private Integer powerHpFrom;
    private Integer powerKwFrom;
    private Integer yearOfConstructionFrom;
    private Integer yearOfConstructionTo;
}