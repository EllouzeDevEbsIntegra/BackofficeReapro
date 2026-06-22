package com.reapro.achat.DTO;

import java.time.LocalDateTime;

/**
 * Résumé d'un utilisateur pour la liste d'administration (RBAC Lot 2).
 * Aucun mot de passe / hash / token n'est exposé.
 */
public record RbacUserSummary(
        Long id,
        String firstname,
        String lastname,
        String email,
        String role,
        boolean active,
        boolean superAdmin,
        LocalDateTime createdAt
) {}
