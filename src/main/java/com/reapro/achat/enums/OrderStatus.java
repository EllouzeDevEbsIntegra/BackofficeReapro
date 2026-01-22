package com.reapro.achat.enums;

public enum OrderStatus {
    DRAFT,      // Brouillon, modifiable
    VALIDATED,  // Validé et envoyé à Business Central
    CANCELLED,  // Annulé
    VALIDATION_ERROR // Erreur lors de la validation BC
}
