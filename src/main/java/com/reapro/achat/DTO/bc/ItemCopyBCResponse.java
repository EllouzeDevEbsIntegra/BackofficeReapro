package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Réponse minimale de BC ItemCopy.
 * On ne mappe que "ref" car c’est tout ce qu’on exploite.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemCopyBCResponse {
    private String ref;
}