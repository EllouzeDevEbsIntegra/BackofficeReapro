package com.reapro.achat.services;

import com.reapro.achat.DTO.LoginRequest;
import com.reapro.achat.DTO.RegisterRequest;
import com.reapro.achat.DTO.TokenResponse;
import com.reapro.achat.config.JwtUtils;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.enums.Role;
import com.reapro.achat.repositories.primary.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires (sans DB ni contexte Spring) du durcissement auth — RBAC Lot 1 :
 *  - inscription publique désactivée → aucun compte créé ;
 *  - inscription (si activée) ne crée jamais de compte ROLE_ADMIN ;
 *  - utilisateur désactivé : login / refresh refusés en 401 ;
 *  - utilisateur actif : login / refresh continuent de fonctionner.
 */
class AuthServiceTest {

    private AdminRepository adminRepository;
    private EmailService emailService;
    private JwtUtils jwtUtils;
    private AuthService authService;

    private static final String STRONG_PWD = "Abcdef1!";

    @BeforeEach
    void setUp() {
        adminRepository = mock(AdminRepository.class);
        emailService = mock(EmailService.class);
        jwtUtils = mock(JwtUtils.class);
        authService = new AuthService(adminRepository, emailService, jwtUtils);
    }

    private RegisterRequest registerRequest() {
        RegisterRequest req = new RegisterRequest();
        req.setFirstname("Jean");
        req.setLastname("Test");
        req.setEmail("new@x.com");
        req.setPassword(STRONG_PWD);
        req.setConfirmPassword(STRONG_PWD);
        return req;
    }

    // ── Tâche 6 : inscription publique désactivée ──────────────────────────────
    @Test
    void register_disabled_throwsForbidden_andCreatesNoUser() {
        ReflectionTestUtils.setField(authService, "registrationEnabled", false);

        ResponseStatusException ex = catchThrowableOfType(
                () -> authService.register(registerRequest()), ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(adminRepository, never()).save(any());
    }

    @Test
    void verifyRegisterCode_disabled_throwsForbidden() {
        ReflectionTestUtils.setField(authService, "registrationEnabled", false);
        com.reapro.achat.DTO.VerifyCodeRequest req = new com.reapro.achat.DTO.VerifyCodeRequest();
        req.setEmail("new@x.com");
        req.setCode("1234");

        ResponseStatusException ex = catchThrowableOfType(
                () -> authService.verifyRegisterCode(req), ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        verify(adminRepository, never()).save(any());
    }

    // ── Tâche 6 : un endpoint public ne crée JAMAIS un compte admin ────────────
    @Test
    void register_enabled_createsNonPrivilegedUser_notAdmin() {
        ReflectionTestUtils.setField(authService, "registrationEnabled", true);
        when(adminRepository.existsByEmail("new@x.com")).thenReturn(false);

        String result = authService.register(registerRequest());

        ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
        verify(adminRepository).save(captor.capture());
        Admin saved = captor.getValue();

        assertThat(saved.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(saved.getRole()).isNotEqualTo(Role.ROLE_ADMIN);
        assertThat(saved.isActive()).isFalse();
        assertThat(result).contains("Code de vérification");
    }

    // ── Tâche 7 : utilisateur désactivé ────────────────────────────────────────
    @Test
    void login_inactiveUser_throwsUnauthorized_andIssuesNoToken() {
        Admin inactive = Admin.builder()
                .id(1L).email("u@x.com").password("hash").active(false).role(Role.ROLE_USER).build();
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(inactive));

        LoginRequest req = new LoginRequest();
        req.setEmail("u@x.com");
        req.setPassword(STRONG_PWD);

        ResponseStatusException ex = catchThrowableOfType(
                () -> authService.login(req), ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verify(jwtUtils, never()).generateAccessToken(anyString());
        verify(jwtUtils, never()).generateRefreshToken(anyString());
    }

    @Test
    void login_unknownUser_throwsUnauthorized() {
        when(adminRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        LoginRequest req = new LoginRequest();
        req.setEmail("ghost@x.com");
        req.setPassword(STRONG_PWD);

        ResponseStatusException ex = catchThrowableOfType(
                () -> authService.login(req), ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void login_activeUser_returnsTokens() {
        String hash = new BCryptPasswordEncoder().encode(STRONG_PWD);
        Admin active = Admin.builder()
                .id(1L).email("u@x.com").password(hash).active(true).role(Role.ROLE_ADMIN).build();
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(active));
        when(jwtUtils.generateAccessToken("u@x.com")).thenReturn("access-tok");
        when(jwtUtils.generateRefreshToken("u@x.com")).thenReturn("refresh-tok");

        LoginRequest req = new LoginRequest();
        req.setEmail("u@x.com");
        req.setPassword(STRONG_PWD);

        TokenResponse tokens = authService.login(req);

        assertThat(tokens.getAccessToken()).isEqualTo("access-tok");
        assertThat(tokens.getRefreshToken()).isEqualTo("refresh-tok");
    }

    @Test
    void refresh_inactiveUser_throwsUnauthorized() {
        when(jwtUtils.extractEmailFromRefreshToken("rt")).thenReturn("u@x.com");
        Admin inactive = Admin.builder()
                .id(1L).email("u@x.com").password("hash").active(false).role(Role.ROLE_USER).build();
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(inactive));

        ResponseStatusException ex = catchThrowableOfType(
                () -> authService.refreshToken("rt"), ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        verify(jwtUtils, never()).generateAccessToken(anyString());
    }

    @Test
    void refresh_activeUser_returnsTokens() {
        when(jwtUtils.extractEmailFromRefreshToken("rt")).thenReturn("u@x.com");
        Admin active = Admin.builder()
                .id(1L).email("u@x.com").password("hash").active(true).role(Role.ROLE_ADMIN).build();
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(active));
        when(jwtUtils.generateAccessToken("u@x.com")).thenReturn("access-tok");
        when(jwtUtils.generateRefreshToken("u@x.com")).thenReturn("refresh-tok");

        TokenResponse tokens = authService.refreshToken("rt");

        assertThat(tokens.getAccessToken()).isEqualTo("access-tok");
        assertThat(tokens.getRefreshToken()).isEqualTo("refresh-tok");
    }
}
