package com.reapro.achat.DTO.bc;

/**
 * Contenu binaire d'une photo article BC + son type MIME.
 * Conteneur interne (jamais sérialisé en JSON) : le binaire est renvoyé tel quel au frontend.
 */
public record BcPictureContent(byte[] content, String contentType) {
}
