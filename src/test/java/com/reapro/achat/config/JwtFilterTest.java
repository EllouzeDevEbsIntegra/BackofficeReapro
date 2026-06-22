package com.reapro.achat.config;

import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.enums.Role;
import com.reapro.achat.repositories.primary.AdminRepository;
import com.reapro.achat.services.PermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests du filtre JWT :
 *  - régression 403 Sync Adaptable : le filtre doit s'exécuter sur le dispatch ASYNC ({@code Mono}/{@code Flux}) ;
 *  - RBAC Lot 1 (SEC) : un token valide d'un utilisateur <b>désactivé</b> ne doit PAS peupler le SecurityContext
 *    (→ 401 via l'authenticationEntryPoint).
 */
class JwtFilterTest {

    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final JwtFilter filter = new JwtFilter(jwtUtils, adminRepository, permissionService);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private Admin admin(boolean active, Role role) {
        return Admin.builder()
                .id(1L)
                .email("u@x.com")
                .password("x")
                .active(active)
                .role(role)
                .build();
    }

    @Test
    void filterRunsOnAsyncDispatch_regressionGuard() {
        // C'EST le correctif : sans ça, le filtre est sauté sur le dispatch async -> 403 sur les Mono.
        assertThat(filter.shouldNotFilterAsyncDispatch()).isFalse();
    }

    @Test
    void validBearer_activeUser_populatesRoleAndPermissionAuthorities() throws Exception {
        when(jwtUtils.extractEmailFromAccessToken("tok")).thenReturn("u@x.com");
        Admin a = admin(true, Role.ROLE_USER);
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(a));
        // RBAC Lot 4 : les permissions effectives sont injectées comme authorities.
        when(permissionService.effectiveCodes(a)).thenReturn(java.util.List.of("B2B_ACCESS", "COMPARATOR_ACCESS"));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sync-adaptable");
        req.addHeader("Authorization", "Bearer tok");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("u@x.com");
        String authorities = auth.getAuthorities().toString();
        assertThat(authorities).contains("ROLE_USER");
        assertThat(authorities).contains("B2B_ACCESS");
        assertThat(authorities).contains("COMPARATOR_ACCESS");
    }

    @Test
    void validBearer_inactiveUser_leavesContextEmpty() throws Exception {
        // SÉCURITÉ : compte désactivé → même avec un token valide, aucune authentification n'est posée.
        when(jwtUtils.extractEmailFromAccessToken("tok")).thenReturn("u@x.com");
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(admin(false, Role.ROLE_ADMIN)));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sync-adaptable");
        req.addHeader("Authorization", "Bearer tok");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void unknownUser_leavesContextEmpty() throws Exception {
        when(jwtUtils.extractEmailFromAccessToken("tok")).thenReturn("ghost@x.com");
        when(adminRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sync-adaptable");
        req.addHeader("Authorization", "Bearer tok");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void noAuthorizationHeader_leavesContextEmpty() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sync-adaptable");
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
