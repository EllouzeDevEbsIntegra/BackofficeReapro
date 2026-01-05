package com.reapro.achat.DTO.bc;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ItemCopyResultResponse {
    // Exemple: "TEST123456 créé avec succès !"
    private String ref;
}