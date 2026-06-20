package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Réponse après suppression de la photo d'un article BC. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PictureDeleteResponse {
    private String itemNo;
    private boolean deleted;
}
