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

    // Erreurs Commandes / Panier
    ORDER_NOT_FOUND(5001, HttpStatus.NOT_FOUND, "Commande ou panier introuvable"),
    ORDER_NOT_DRAFT(5002, HttpStatus.BAD_REQUEST, "Le panier n'est pas modifiable car il n'est plus en brouillon"),
    ORDER_LINE_NOT_FOUND(5003, HttpStatus.NOT_FOUND, "Ligne de commande introuvable"),
    INVALID_QUANTITY(5004, HttpStatus.BAD_REQUEST, "Quantité invalide"),
    STOCK_INSUFFICIENT(5005, HttpStatus.BAD_REQUEST, "Stock insuffisant"),
    ORDER_EMPTY(5006, HttpStatus.BAD_REQUEST, "Le panier est vide"),
    ITEM_NOT_FOUND(5007, HttpStatus.NOT_FOUND, "Article introuvable"),
    CLIENT_ID_REQUIRED(5008, HttpStatus.BAD_REQUEST, "Le clientId est obligatoire pour cette opération"), // Nouveau

    // Autres erreurs
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
