package com.reapro.achat.partslink;

/**
 * Levée quand un utilisateur tente d'accéder à un job Partslink qui ne lui appartient pas.
 * Isolation stricte par utilisateur : un user ne peut jamais lire le job d'un autre.
 */
public class PartslinkJobAccessException extends RuntimeException {
    public PartslinkJobAccessException(String message) {
        super(message);
    }
}
