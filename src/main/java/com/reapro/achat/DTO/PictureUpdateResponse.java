package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Réponse après mise à jour de la photo d'un article BC. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PictureUpdateResponse {
    private String itemNo;
    private boolean updated;
    private String contentType;
}
