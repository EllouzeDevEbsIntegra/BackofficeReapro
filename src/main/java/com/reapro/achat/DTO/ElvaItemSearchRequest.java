package com.reapro.achat.DTO;

import lombok.Data;

@Data
public class ElvaItemSearchRequest {
    private String searchTerm; // Champ générique pour la recherche multi-critères
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
