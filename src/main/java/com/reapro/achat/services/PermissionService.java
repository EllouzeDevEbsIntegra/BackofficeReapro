package com.reapro.achat.services;

import com.reapro.achat.DTO.ResolvedPermissions;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.entities.primary.UserPermission;
import com.reapro.achat.enums.Role;
import com.reapro.achat.repositories.primary.AdminRepository;
import com.reapro.achat.repositories.primary.PermissionRepository;
import com.reapro.achat.repositories.primary.UserPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Résolution des permissions effectives d'un utilisateur (Lot 1).
 *
 * <p>Stratégie de compatibilité (transitoire) : tant qu'aucun {@code SUPER_ADMIN} dédié n'existe,
 * {@link Role#ROLE_ADMIN} est traité comme <b>super-admin</b> et reçoit automatiquement <b>toutes</b>
 * les permissions actives — sans écrire d'affectations en base, ce qui préserve les comptes existants.
 * Modèle cible : un seul SUPER_ADMIN principal (voir Reapro-RBAC-permissions-roadmap.md).</p>
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final AdminRepository adminRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;

    /** Super-admin transitoire : tout compte {@code ROLE_ADMIN} aujourd'hui. */
    public boolean isSuperAdmin(Admin admin) {
        return admin != null && admin.getRole() == Role.ROLE_ADMIN;
    }

    /**
     * Permissions effectives pour l'email authentifié.
     * Renvoie {@link ResolvedPermissions#none()} si l'utilisateur est introuvable ou désactivé.
     */
    @Transactional(readOnly = true)
    public ResolvedPermissions resolveForEmail(String email) {
        Admin admin = adminRepository.findByEmail(email).orElse(null);
        if (admin == null || !admin.isActive()) {
            return ResolvedPermissions.none();
        }
        return new ResolvedPermissions(isSuperAdmin(admin), effectiveCodes(admin));
    }

    /**
     * Codes de permissions effectifs d'un utilisateur (déjà chargé).
     * Super-admin → toutes les permissions actives ; sinon → permissions actives affectées.
     * Utilisé par {@code JwtFilter} pour injecter les authorities Spring Security (Lot 4).
     */
    @Transactional(readOnly = true)
    public List<String> effectiveCodes(Admin admin) {
        if (admin == null || !admin.isActive()) {
            return List.of();
        }

        if (isSuperAdmin(admin)) {
            return permissionRepository.findByActiveTrue().stream()
                    .map(p -> p.getCode())
                    .sorted()
                    .toList();
        }

        Set<Long> permissionIds = userPermissionRepository.findByUserId(admin.getId()).stream()
                .map(UserPermission::getPermissionId)
                .collect(Collectors.toSet());

        if (permissionIds.isEmpty()) {
            return List.of();
        }

        return permissionRepository.findAllById(permissionIds).stream()
                .filter(p -> p.isActive())
                .map(p -> p.getCode())
                .sorted()
                .toList();
    }
}
