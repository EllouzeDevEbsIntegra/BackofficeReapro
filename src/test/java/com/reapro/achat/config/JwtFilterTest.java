package com.reapro.achat.config;

import com.reapro.achat.repositories.primary.AdminRepository;
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
 * Régression 403 Sync Adaptable : le filtre JWT doit s'exécuter aussi sur le dispatch ASYNC
 * (endpoints renvoyant {@code Mono}/{@code Flux}), sinon le SecurityContext n'est pas repeuplé
 * et {@code .authenticated()} refuse (403) alors que le token est valide.
 */
class JwtFilterTest {

    private final JwtUtils jwtUtils = mock(JwtUtils.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final JwtFilter filter = new JwtFilter(jwtUtils, adminRepository);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filterRunsOnAsyncDispatch_regressionGuard() {
        // C'EST le correctif : sans ça, le filtre est sauté sur le dispatch async -> 403 sur les Mono.
        assertThat(filter.shouldNotFilterAsyncDispatch()).isFalse();
    }

    @Test
    void validBearer_populatesSecurityContext() throws Exception {
        when(jwtUtils.extractEmailFromAccessToken("tok")).thenReturn("u@x.com");
        when(adminRepository.existsByEmail("u@x.com")).thenReturn(true);
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.empty()); // -> ROLE_ADMIN par défaut

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sync-adaptable");
        req.addHeader("Authorization", "Bearer tok");

        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getName()).isEqualTo("u@x.com");
        assertThat(auth.getAuthorities().toString()).contains("ROLE_ADMIN");
    }

    @Test
    void noAuthorizationHeader_leavesContextEmpty() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/sync-adaptable");
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
