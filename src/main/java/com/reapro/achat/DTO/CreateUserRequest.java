package com.reapro.achat.DTO;

/**
 * Création d'un utilisateur par un gestionnaire (RBAC Lot 2).
 * Le compte est toujours créé en {@code ROLE_USER} (non privilégié) — la création de comptes
 * privilégiés via API est volontairement interdite (anti-escalade).
 */
public record CreateUserRequest(
        String firstname,
        String lastname,
        String email,
        String password
) {}
