package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CompareQuoteLineDTO {
    private String systemId;
    private String compareQuoteNo;
    private String itemNo;
    private LocalDateTime creationDate;
    private Integer pageNumber;
    private String structuredDescription;
    private Integer countItemManual; // This will be the calculated value
    private Integer nbLineNotThreated;
    private String itemProductCode;
    private String itemSubProductCode;
    private String groupe;
    private String sousGroupe;
    private String champsLibre;
    private String produit;
    private String makeCode;
}
