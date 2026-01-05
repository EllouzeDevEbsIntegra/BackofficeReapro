package com.reapro.achat.DTO;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class ApiErrorResponse {
    private int status;       // ex: 400
    private String message;   // ex: "Ancien mot de passe incorrect."
}