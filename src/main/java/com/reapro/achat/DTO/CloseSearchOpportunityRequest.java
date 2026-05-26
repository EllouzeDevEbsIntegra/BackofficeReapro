package com.reapro.achat.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CloseSearchOpportunityRequest {

    @NotBlank(message = "Le diagnosticStatus est obligatoire")
    private String diagnosticStatus;

    @NotBlank(message = "Le actionType est obligatoire")
    private String actionType;

    private String closureReason;
    private String comment;
    private Long linkedArticleId;

    // Nouveau champ pour gérer l'exclusion
    private boolean excludeFromFutureSync = false;
}
