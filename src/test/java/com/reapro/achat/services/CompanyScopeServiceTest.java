package com.reapro.achat.services;

import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.enums.Role;
import com.reapro.achat.repositories.primary.AdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Lot 4bis-A — la société BC est résolue depuis l'utilisateur authentifié (anti company-spoofing).
 */
class CompanyScopeServiceTest {

    private AdminRepository adminRepository;
    private CompanyScopeService service;

    @BeforeEach
    void setUp() {
        adminRepository = mock(AdminRepository.class);
        service = new CompanyScopeService(adminRepository);
    }

    private Admin admin(String email, String companyId) {
        return Admin.builder().id(1L).email(email).role(Role.ROLE_USER).active(true)
                .bcCompanyId(companyId).build();
    }

    @Test
    void usesCompanyFromProfile_ignoringAnyClientValue() {
        // L'utilisateur a la société A ; le service renvoie TOUJOURS la société du profil.
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(admin("u@x.com", "COMP-A")));

        assertThat(service.requireUserCompanyId("u@x.com")).isEqualTo("COMP-A");
    }

    @Test
    void noCompanyAssigned_throws403() {
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(admin("u@x.com", null)));

        ResponseStatusException ex = catchThrowableOfType(
                () -> service.requireUserCompanyId("u@x.com"), ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void blankCompany_throws403() {
        when(adminRepository.findByEmail("u@x.com")).thenReturn(Optional.of(admin("u@x.com", "   ")));

        ResponseStatusException ex = catchThrowableOfType(
                () -> service.requireUserCompanyId("u@x.com"), ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void unknownUser_throws401() {
        when(adminRepository.findByEmail("ghost@x.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = catchThrowableOfType(
                () -> service.requireUserCompanyId("ghost@x.com"), ResponseStatusException.class);

        assertThat(ex).isNotNull();
        assertThat(ex.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }
}
