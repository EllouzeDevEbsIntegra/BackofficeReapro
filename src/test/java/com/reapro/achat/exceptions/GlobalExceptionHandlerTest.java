package com.reapro.achat.exceptions;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verrouille la sémantique HTTP des erreurs RBAC (Lot 4 / fix 403) :
 *  - AccessDeniedException (refus @PreAuthorize) → 403, PAS 400 ;
 *  - ResponseStatusException 401 (session invalide / inactif) → 401 ;
 *  - autre RuntimeException → 400 (validation/erreur applicative).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void accessDenied_returns403_notBadRequest() {
        ResponseEntity<String> resp = handler.handleAccessDenied(new AccessDeniedException("Access Denied"));

        assertThat(resp.getStatusCode().value()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(resp.getBody()).isEqualTo("Access Denied");
    }

    @Test
    void unauthorizedResponseStatus_stays401() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Compte désactivé.");
        var resp = handler.handleResponseStatus(ex);

        assertThat(resp.getStatusCode().value()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    @Test
    void genericRuntime_stays400() {
        ResponseEntity<String> resp = handler.handleRuntime(new RuntimeException("validation"));

        assertThat(resp.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    }
}
