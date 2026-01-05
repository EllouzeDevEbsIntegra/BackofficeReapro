package com.reapro.achat.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.reapro.achat.DTO.ApiErrorResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;


@RestControllerAdvice
public class GlobalExceptionHandler {

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
