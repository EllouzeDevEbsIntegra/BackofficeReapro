package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ResolvedPermissions;
import com.reapro.achat.DTO.SessionInfoResponse;
import com.reapro.achat.services.AuthService;
import com.reapro.achat.services.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Test unitaire (sans DB ni contexte web) de {@code GET /api/auth/me} enrichi — RBAC Lot 1 :
 * la réponse expose les permissions effectives + le flag superAdmin.
 */
class AuthControllerTest {

    private final AuthService authService = mock(AuthService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final AuthController controller = new AuthController(authService, permissionService);

    @Test
    void me_returnsRolesAndPermissions() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "u@x.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        when(permissionService.resolveForEmail("u@x.com"))
                .thenReturn(new ResolvedPermissions(true, List.of("B2B_ACCESS", "COMPARATOR_ACCESS")));

        SessionInfoResponse res = controller.me(auth);

        assertThat(res.isAuthenticated()).isTrue();
        assertThat(res.getEmail()).isEqualTo("u@x.com");
        assertThat(res.getRoles()).contains("ROLE_ADMIN");
        assertThat(res.isSuperAdmin()).isTrue();
        assertThat(res.getPermissions()).containsExactly("B2B_ACCESS", "COMPARATOR_ACCESS");
    }

    @Test
    void me_normalUser_returnsGrantedPermissionsOnly() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "user@x.com", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(permissionService.resolveForEmail("user@x.com"))
                .thenReturn(new ResolvedPermissions(false, List.of("B2B_ACCESS")));

        SessionInfoResponse res = controller.me(auth);

        assertThat(res.isSuperAdmin()).isFalse();
        assertThat(res.getRoles()).contains("ROLE_USER");
        assertThat(res.getPermissions()).containsExactly("B2B_ACCESS");
    }
}
