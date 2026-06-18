package com.reapro.achat.DTO.pausegame;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Une question du « Défi Fabricant » : une référence article réelle + 4 choix de fabricants
 * (1 correct, 3 distincts incorrects, ordre mélangé). Aucune donnée sensible.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManufacturerQuestion {
    private String itemNo;                  // référence article réelle (ELVA_Item.No_)
    private String reference;               // = itemNo (alias attendu côté front)
    private String description;             // libellé article optionnel (peut être null)
    private String correctManufacturerId;   // id du bon fabricant (= choix.id correct)
    private String correctManufacturerName; // nom du bon fabricant
    private List<ManufacturerChoice> choices; // exactement 4 choix, mélangés
}
