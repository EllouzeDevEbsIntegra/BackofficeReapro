package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Vue minimale d'un item via l'API STANDARD Microsoft ({@code items}),
 * utilisée pour résoudre l'itemNo (champ {@code number}) vers le systemId ({@code id}).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcStandardItem {

    @JsonProperty("@odata.etag")
    private String etag;

    @JsonProperty("id")
    private String id;

    @JsonProperty("number")
    private String number;
}
