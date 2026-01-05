package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcManufacturer {
    @JsonProperty("Code")
    private String code;

    @JsonProperty("Name")
    private String name;

    @JsonProperty("IDTechDOC")
    private String idTechDoc; // Le lien avec TecDoc (dataSupplierId)

    @JsonProperty("vendorNo")
    private String vendorNo;
}