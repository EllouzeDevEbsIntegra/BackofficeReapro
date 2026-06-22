package com.reapro.achat.DTO;

/** Mise à jour des informations simples d'un utilisateur (RBAC Lot 2). Champs null = inchangés. */
public record UpdateUserInfoRequest(
        String firstname,
        String lastname,
        String email
) {}
