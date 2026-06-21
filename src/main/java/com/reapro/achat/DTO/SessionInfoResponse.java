package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse de validation de session ({@code GET /api/auth/me}).
 * Sert au frontend à distinguer « session invalide » (401) d'un « vrai 403 droits insuffisants »,
 * et fournit les <b>permissions effectives</b> de l'utilisateur (RBAC Lot 1).
 * Aucune donnée sensible n'est exposée.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfoResponse {
    private boolean authenticated;
    private String email;
    private List<String> roles;
    /** {@code true} pour le super-admin (transitoire : {@code ROLE_ADMIN}) → accès total. */
    private boolean superAdmin;
    /** Codes de permissions effectifs ; le frontend peut s'en servir directement pour le masquage. */
    private List<String> permissions;
}
