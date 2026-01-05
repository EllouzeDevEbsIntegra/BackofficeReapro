package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)  // ❗ n'envoie pas les champs null
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemCopyBCBody {

    @JsonProperty("ref")
    private String ref;

    @JsonProperty("frs")
    private String frs;

    @JsonProperty("refTecdoc")
    private String refTecdoc;

    @JsonProperty("refMaster")
    private String refMaster;

    @JsonProperty("category")   // toujours "PR"
    private String category;

    @JsonProperty("group")
    private String group;

    // ⚠ Casse exacte comme dans BC
    @JsonProperty("SubGroup")
    private String subGroup;

    @JsonProperty("ChampsLibre")
    private String champsLibre;

    @JsonProperty("Manufacturer")
    private String manufacturer;

    @JsonProperty("marque")
    private String marque;
}