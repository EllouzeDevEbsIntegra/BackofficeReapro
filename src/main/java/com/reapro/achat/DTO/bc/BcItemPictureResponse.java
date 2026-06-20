package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Réponse BC (API beta) pour {@code items({itemId})/picture} :
 * la photo est encapsulée dans un wrapper {@code { "value": [ {...} ] }}.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcItemPictureResponse extends BcListResponse<BcItemPicture> {
}
