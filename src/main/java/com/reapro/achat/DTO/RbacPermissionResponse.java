package com.reapro.achat.DTO;

/** Permission disponible exposée à l'UI d'administration (RBAC Lot 2). */
public record RbacPermissionResponse(
        String code,
        String label,
        String description,
        String group,
        String type,
        boolean sensitive,
        boolean active
) {}
