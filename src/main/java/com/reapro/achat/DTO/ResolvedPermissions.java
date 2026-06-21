package com.reapro.achat.DTO;

import java.util.List;

/**
 * Permissions effectives résolues pour un utilisateur.
 * {@code superAdmin = true} ⇒ accès total (toutes les permissions actives sont renvoyées).
 */
public record ResolvedPermissions(boolean superAdmin, List<String> permissions) {

    public static ResolvedPermissions none() {
        return new ResolvedPermissions(false, List.of());
    }
}
