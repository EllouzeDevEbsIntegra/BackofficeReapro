package com.reapro.achat.Controller;

import com.reapro.achat.DTO.pausegame.ManufacturerChallengeResponse;
import com.reapro.achat.services.PauseGameService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Endpoints des mini-jeux « Pause Reapro ». Authentifié comme le reste de l'API
 * (règle globale {@code anyRequest().authenticated()} de SecurityConfig — pas de modification).
 * Ne retourne aucune donnée métier sensible ni secret.
 */
@RestController
@RequestMapping("/api/pause-games")
@RequiredArgsConstructor
public class PauseGameController {

    private final PauseGameService pauseGameService;

    /** Série de questions « référence article → bon fabricant » (4 choix chacune). */
    @GetMapping("/manufacturer-challenge")
    public ManufacturerChallengeResponse manufacturerChallenge(
            @RequestParam(name = "count", defaultValue = "25") int count) {
        return pauseGameService.generateManufacturerChallenge(count);
    }

    /** Photo de la pièce (TecDoc) pour une référence — best-effort, peut renvoyer {imageUrl:null}. */
    @GetMapping("/part-image")
    public Map<String, String> partImage(@RequestParam(name = "reference") String reference) {
        Map<String, String> body = new HashMap<>();
        body.put("imageUrl", pauseGameService.getPartImage(reference));
        return body;
    }
}
