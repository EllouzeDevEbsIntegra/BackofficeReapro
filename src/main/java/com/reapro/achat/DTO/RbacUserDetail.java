package com.reapro.achat.DTO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Détail d'un utilisateur + ses permissions effectives (RBAC Lot 2).
 * Aucun mot de passe / hash / token n'est exposé.
 */
public record RbacUserDetail(
        Long id,
        String firstname,
        String lastname,
        String email,
        String role,
        boolean active,
        boolean superAdmin,
        String bcCompanyId,
        String bcCompanyName,
        List<String> permissions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
