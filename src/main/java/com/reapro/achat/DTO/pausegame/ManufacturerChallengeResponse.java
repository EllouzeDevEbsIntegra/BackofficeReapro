package com.reapro.achat.DTO.pausegame;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse de l'endpoint {@code GET /api/pause-games/manufacturer-challenge} :
 * une série de questions prêtes à jouer pour le mini-jeu « Défi Fabricant ».
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ManufacturerChallengeResponse {
    private List<ManufacturerQuestion> questions;
}
