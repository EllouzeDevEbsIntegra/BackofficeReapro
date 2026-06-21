package com.reapro.achat.services;

import com.reapro.achat.DTO.ResolvedPermissions;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.entities.primary.Permission;
import com.reapro.achat.entities.primary.UserPermission;
import com.reapro.achat.enums.PermissionType;
import com.reapro.achat.enums.Role;
import com.reapro.achat.repositories.primary.AdminRepository;
import com.reapro.achat.repositories.primary.PermissionRepository;
import com.reapro.achat.repositories.primary.UserPermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires (sans DB) de la résolution des permissions effectives — RBAC Lot 1.
 */
class PermissionServiceTest {

    private AdminRepository adminRepository;
    private PermissionRepository permissionRepository;
    private UserPermissionRepository userPermissionRepository;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        adminRepository = mock(AdminRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        userPermissionRepository = mock(UserPermissionRepository.class);
        service = new PermissionService(adminRepository, permissionRepository, userPermissionRepository);
    }

    private Admin admin(Long id, boolean active, Role role) {
        return Admin.builder().id(id).email("u@x.com").password("x").active(active).role(role).build();
    }

    private Permission perm(Long id, String code) {
        return Permission.builder().id(id).code(code).label(code).type(PermissionType.MODULE).active(true).build();
    }

    @Test
    void superAdmin_getsAllActivePermissions() {
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(admin(1L, true, Role.ROLE_ADMIN)));
        when(permissionRepository.findByActiveTrue())
                .thenReturn(List.of(perm(2L, "COMPARATOR_ACCESS"), perm(1L, "B2B_ACCESS")));

        ResolvedPermissions resolved = service.resolveForEmail("u@x.com");

        assertThat(resolved.superAdmin()).isTrue();
        // triées
        assertThat(resolved.permissions()).containsExactly("B2B_ACCESS", "COMPARATOR_ACCESS");
    }

    @Test
    void normalUser_getsOnlyGrantedPermissions() {
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(admin(5L, true, Role.ROLE_USER)));
        when(userPermissionRepository.findByUserId(5L))
                .thenReturn(List.of(UserPermission.builder().userId(5L).permissionId(10L).build()));
        when(permissionRepository.findAllById(any()))
                .thenReturn(List.of(perm(10L, "B2B_ACCESS")));

        ResolvedPermissions resolved = service.resolveForEmail("u@x.com");

        assertThat(resolved.superAdmin()).isFalse();
        assertThat(resolved.permissions()).containsExactly("B2B_ACCESS");
    }

    @Test
    void normalUser_withoutGrants_getsEmpty() {
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(admin(5L, true, Role.ROLE_USER)));
        when(userPermissionRepository.findByUserId(5L)).thenReturn(List.of());

        ResolvedPermissions resolved = service.resolveForEmail("u@x.com");

        assertThat(resolved.superAdmin()).isFalse();
        assertThat(resolved.permissions()).isEmpty();
    }

    @Test
    void inactiveUser_getsNone_evenIfAdminRole() {
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(admin(1L, false, Role.ROLE_ADMIN)));

        ResolvedPermissions resolved = service.resolveForEmail("u@x.com");

        assertThat(resolved.superAdmin()).isFalse();
        assertThat(resolved.permissions()).isEmpty();
    }

    @Test
    void unknownUser_getsNone() {
        when(adminRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        ResolvedPermissions resolved = service.resolveForEmail("ghost@x.com");

        assertThat(resolved.superAdmin()).isFalse();
        assertThat(resolved.permissions()).isEmpty();
    }
}
