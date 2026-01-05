package com.reapro.achat.exceptions;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // Erreurs Business Central
    BC_API_ERROR(4005, HttpStatus.BAD_GATEWAY, "Erreur API Business Central"),
    BC_CONNECTION_ERROR(4001, HttpStatus.SERVICE_UNAVAILABLE, "Impossible de se connecter à Business Central"),

    // Erreurs Paramètres
    PARAMETER_NOT_FOUND(3001, HttpStatus.NOT_FOUND, "Paramètre introuvable"),

    // Autres erreurs (tu pourras en ajouter d'autres ici)
    INTERNAL_ERROR(1000, HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur"),
    RESOURCE_NOT_FOUND(1002, HttpStatus.NOT_FOUND, "Ressource introuvable");

    private final int code;
    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(int code, HttpStatus httpStatus, String defaultMessage) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }
}