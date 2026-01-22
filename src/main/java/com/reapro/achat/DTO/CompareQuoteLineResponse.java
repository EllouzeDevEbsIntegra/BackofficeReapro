package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class CompareQuoteLineResponse {

    private String systemId;
    private String compareQuoteNo;
    private String itemNo;
    private LocalDateTime creationDate;
    private Integer pageNumber;
    private String structuredDescription;
    // private Integer countItemManual; // Removed as requested
    private Integer nbLineNotThreated;
    private Boolean treated;
    private String itemProductCode;
    private String itemSubProductCode;
    private String groupe;
    private String sousGroupe;
    private String champsLibre;
    private String produit;
    private String makeCode;
}
