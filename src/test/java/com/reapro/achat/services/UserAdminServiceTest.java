package com.reapro.achat.services;

import com.reapro.achat.DTO.CreateUserRequest;
import com.reapro.achat.DTO.RbacUserDetail;
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
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires (sans DB ni contexte Spring) du Lot 2 RBAC — gestion utilisateurs & permissions.
 * Couvre les règles d'autorisation et l'anti-escalade.
 */
class UserAdminServiceTest {

    private AdminRepository adminRepository;
    private PermissionRepository permissionRepository;
    private UserPermissionRepository userPermissionRepository;
    private EmailService emailService;
    private UserAdminService service;

    private static final String SUPER = "super@x.com";
    private static final String PM = "pm@x.com";      // permission manager (PERMISSION_ASSIGNMENT_ACCESS)
    private static final String PLAIN = "plain@x.com"; // utilisateur sans droit
    private static final String INACTIVE = "inact@x.com";

    @BeforeEach
    void setUp() {
        adminRepository = mock(AdminRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        userPermissionRepository = mock(UserPermissionRepository.class);
        emailService = mock(EmailService.class);
        service = new UserAdminService(adminRepository, permissionRepository, userPermissionRepository, emailService);
    }

    private Admin admin(Long id, String email, Role role, boolean active) {
        return Admin.builder().id(id).email(email).password("hash").role(role).active(active).build();
    }

    private Permission perm(Long id, String code, boolean sensitive, PermissionType type) {
        return Permission.builder().id(id).code(code).label(code).type(type).sensitive(sensitive).active(true).build();
    }

    /** Configure l'acteur "permission manager" : ROLE_USER possédant PERMISSION_ASSIGNMENT_ACCESS. */
    private void wirePermissionManager() {
        Admin pm = admin(10L, PM, Role.ROLE_USER, true);
        when(adminRepository.findByEmail(PM)).thenReturn(Optional.of(pm));
        when(userPermissionRepository.findByUserId(10L))
                .thenReturn(List.of(UserPermission.builder().userId(10L).permissionId(100L).build()));
        when(permissionRepository.findById(100L))
                .thenReturn(Optional.of(perm(100L, "PERMISSION_ASSIGNMENT_ACCESS", true, PermissionType.ADMIN)));
    }

    // ── superAdmin peut lister ──────────────────────────────────────────────
    @Test
    void superAdmin_canListUsers() {
        when(adminRepository.findByEmail(SUPER)).thenReturn(Optional.of(admin(1L, SUPER, Role.ROLE_ADMIN, true)));
        Pageable pageable = PageRequest.of(0, 10);
        when(adminRepository.findAll(pageable))
                .thenReturn(new PageImpl<>(List.of(admin(2L, "u@x.com", Role.ROLE_USER, true))));

        Page<?> page = service.listUsers(SUPER, pageable, null);

        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    // ── superAdmin peut attribuer une permission sensible ───────────────────
    @Test
    void superAdmin_canAssignSensitivePermission() {
        when(adminRepository.findByEmail(SUPER)).thenReturn(Optional.of(admin(1L, SUPER, Role.ROLE_ADMIN, true)));
        when(adminRepository.findById(2L)).thenReturn(Optional.of(admin(2L, "u@x.com", Role.ROLE_USER, true)));
        when(permissionRepository.findByCode("USER_MANAGEMENT_ACCESS"))
                .thenReturn(Optional.of(perm(50L, "USER_MANAGEMENT_ACCESS", true, PermissionType.ADMIN)));
        when(userPermissionRepository.existsByUserIdAndPermissionId(2L, 50L)).thenReturn(false);

        RbacUserDetail detail = service.assignPermissions(SUPER, 2L, List.of("USER_MANAGEMENT_ACCESS"));

        assertThat(detail).isNotNull();
        ArgumentCaptor<UserPermission> captor = ArgumentCaptor.forClass(UserPermission.class);
        verify(userPermissionRepository).save(captor.capture());
        assertThat(captor.getValue().getPermissionId()).isEqualTo(50L);
        assertThat(captor.getValue().getGrantedBy()).isEqualTo(1L);
    }

    // ── permission manager peut attribuer une permission métier NON sensible ─
    @Test
    void permissionManager_canAssignNonSensitive() {
        wirePermissionManager();
        when(adminRepository.findById(2L)).thenReturn(Optional.of(admin(2L, "u@x.com", Role.ROLE_USER, true)));
        when(permissionRepository.findByCode("B2B_ACCESS"))
                .thenReturn(Optional.of(perm(60L, "B2B_ACCESS", false, PermissionType.MODULE)));
        when(userPermissionRepository.existsByUserIdAndPermissionId(2L, 60L)).thenReturn(false);

        service.assignPermissions(PM, 2L, List.of("B2B_ACCESS"));

        verify(userPermissionRepository).save(any(UserPermission.class));
    }

    // ── permission manager NE peut PAS attribuer une permission sensible ─────
    @Test
    void permissionManager_cannotAssignSensitive_403() {
        wirePermissionManager();
        when(adminRepository.findById(2L)).thenReturn(Optional.of(admin(2L, "u@x.com", Role.ROLE_USER, true)));
        when(permissionRepository.findByCode("USER_MANAGEMENT_ACCESS"))
                .thenReturn(Optional.of(perm(50L, "USER_MANAGEMENT_ACCESS", true, PermissionType.ADMIN)));

        ResponseStatusException ex = catchThrowableOfType(
                () -> service.assignPermissions(PM, 2L, List.of("USER_MANAGEMENT_ACCESS")),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(userPermissionRepository, never()).save(any());
    }

    // ── utilisateur sans droit → 403 ────────────────────────────────────────
    @Test
    void userWithoutRights_listUsers_403() {
        when(adminRepository.findByEmail(PLAIN)).thenReturn(Optional.of(admin(20L, PLAIN, Role.ROLE_USER, true)));
        when(userPermissionRepository.findByUserId(20L)).thenReturn(List.of());

        ResponseStatusException ex = catchThrowableOfType(
                () -> service.listUsers(PLAIN, PageRequest.of(0, 10), null),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    // ── utilisateur inactif reste refusé (401) ──────────────────────────────
    @Test
    void inactiveActor_isRejected_401() {
        when(adminRepository.findByEmail(INACTIVE)).thenReturn(Optional.of(admin(30L, INACTIVE, Role.ROLE_ADMIN, false)));

        ResponseStatusException ex = catchThrowableOfType(
                () -> service.listUsers(INACTIVE, PageRequest.of(0, 10), null),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    // ── permission manager NE peut PAS créer d'utilisateur (réservé user-mgmt) ─
    @Test
    void permissionManager_cannotCreateUser_403() {
        wirePermissionManager();

        ResponseStatusException ex = catchThrowableOfType(
                () -> service.createUser(PM, new CreateUserRequest("A", "B", "new@x.com", "Abcdef1!")),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(adminRepository, never()).save(any());
    }

    // ── anti-lock : impossible de modifier son propre statut ────────────────
    @Test
    void cannotChangeOwnActiveStatus_400() {
        when(adminRepository.findByEmail(SUPER)).thenReturn(Optional.of(admin(1L, SUPER, Role.ROLE_ADMIN, true)));
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin(1L, SUPER, Role.ROLE_ADMIN, true)));

        ResponseStatusException ex = catchThrowableOfType(
                () -> service.setActive(SUPER, 1L, false),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    // ── anti-lock : impossible de retirer ses propres permissions critiques ──
    @Test
    void cannotRevokeOwnCriticalPermission_403() {
        when(adminRepository.findByEmail(SUPER)).thenReturn(Optional.of(admin(1L, SUPER, Role.ROLE_ADMIN, true)));
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin(1L, SUPER, Role.ROLE_ADMIN, true)));
        when(permissionRepository.findByCode("PERMISSION_ASSIGNMENT_ACCESS"))
                .thenReturn(Optional.of(perm(100L, "PERMISSION_ASSIGNMENT_ACCESS", true, PermissionType.ADMIN)));

        ResponseStatusException ex = catchThrowableOfType(
                () -> service.revokePermissions(SUPER, 1L, List.of("PERMISSION_ASSIGNMENT_ACCESS")),
                ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(userPermissionRepository, never()).delete(any());
    }
}
