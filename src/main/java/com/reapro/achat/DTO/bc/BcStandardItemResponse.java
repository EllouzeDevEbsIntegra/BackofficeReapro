package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Réponse BC (API standard) pour {@code items} : wrapper {@code { "value": [ ... ] }}.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcStandardItemResponse extends BcListResponse<BcStandardItem> {
}
