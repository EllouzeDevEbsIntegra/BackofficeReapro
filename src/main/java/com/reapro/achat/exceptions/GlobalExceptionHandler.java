package com.reapro.achat.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.reapro.achat.DTO.ApiErrorResponse;
import com.reapro.achat.DTO.salesorder.StockValidationErrorResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;


@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * RBAC : un refus d'autorisation ({@code @PreAuthorize}) lève une {@link AccessDeniedException}
     * PENDANT l'invocation du contrôleur ; sans ce handler dédié, elle tomberait dans le catch-all
     * {@code RuntimeException} ci-dessous et renverrait 400. Ici on garantit la sémantique correcte :
     * authentifié mais sans permission → <b>403 Forbidden</b>.
     * (Le cas « non authentifié / token invalide / utilisateur inactif » → 401 reste géré en amont par
     * l'{@code authenticationEntryPoint} de SecurityConfig, le contrôleur n'étant jamais atteint.)
     * Ce handler doit primer sur {@link #handleRuntime(RuntimeException)} (handler plus spécifique).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<String> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access Denied");
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException ex) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(ex.getErrorCode().getHttpStatus().value())
                .message(ex.getMessage() != null ? ex.getMessage() : ex.getErrorCode().getDefaultMessage())
                .build();
        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(body);
    }

    @ExceptionHandler(StockValidationException.class)
    public ResponseEntity<StockValidationErrorResponse> handleStockValidationException(StockValidationException ex) {
        StockValidationErrorResponse response = StockValidationErrorResponse.builder()
                .message(ex.getMessage())
                .invalidLines(ex.getInvalidLines())
                .build();
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode statusCode = ex.getStatusCode();
        int status = statusCode.value();

        String message = ex.getReason();
        if (message == null || message.isBlank()) {
            message = statusCode.toString();
        }

        ApiErrorResponse body = ApiErrorResponse.builder()
                .status(status)
                .message(message)
                .build();

        return ResponseEntity.status(statusCode).body(body);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntime(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
