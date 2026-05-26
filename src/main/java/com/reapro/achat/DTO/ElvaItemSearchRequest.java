package com.reapro.achat.DTO;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ElvaItemSearchRequest {
    @NotBlank(message = "Le clientId est obligatoire pour récupérer les prix spécifiques.")
    private String clientId; // Devenu obligatoire

    private String searchTerm;
    private String no;
    private String description;
    private String searchDescription;
    private String description2;
    private String vendorItemNo;
    private String makeCode;
    private String descriptionStructuree;
    private String itemProductCode;
    private String itemSubProductCode;
    private String groupe;
    private String sousGroupe;
    private String referenceOrigineLie;
    private String codeFabricant;
    private String fabricant;
}
