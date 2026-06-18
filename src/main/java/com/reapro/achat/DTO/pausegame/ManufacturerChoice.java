package com.reapro.achat.DTO.pausegame;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un choix de fabricant proposé dans le mini-jeu « Défi Fabricant ».
 * {@code logoUrl} est optionnel : {@code null} quand aucun logo TecDoc fiable n'est disponible
 * (le front affiche alors le nom du fabricant — fallback obligatoire).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManufacturerChoice {
    private String id;       // clé stable du fabricant (nom normalisé majuscules) — sert au scoring côté front
    private String name;     // nom affiché
    private String logoUrl;  // logo TecDoc si disponible, sinon null (fallback = name)
}
