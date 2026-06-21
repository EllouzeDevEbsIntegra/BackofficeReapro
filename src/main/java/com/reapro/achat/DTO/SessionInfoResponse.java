package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse minimale de validation de session ({@code GET /api/auth/me}).
 * Sert au frontend à distinguer « session invalide » (401) d'un « vrai 403 droits insuffisants ».
 * Volontairement léger : pas d'accès base, aucune donnée sensible.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfoResponse {
    private boolean authenticated;
    private String email;
    private List<String> roles;
}
