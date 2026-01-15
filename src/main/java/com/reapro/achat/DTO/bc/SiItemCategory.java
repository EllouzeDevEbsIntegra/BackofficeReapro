package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SiItemCategory {

    @JsonProperty("id")
    private String id;

    @JsonProperty("code")
    private String code;

    @JsonProperty("displayName")
    private String description;

    @JsonProperty("Indentation")
    private Integer indentation;

    @JsonProperty("ParentCategory")
    private String parentCategory;
}
