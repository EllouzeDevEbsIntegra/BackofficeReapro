package com.reapro.achat.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CustomerFinancialDetailResponse {
    
    private String codeClient;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal plafondCommercial; // Credit Limit (LCY)

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal encoursEncaissementEnCoffre;

    private boolean contreRemboursement;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal encoursCommercial; // (Commande ouverte + Livré non facturé - retourNonfacturé)

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal factureEtAvoirs; // (FactureNonRéglée - AvoirNonRéglée)

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal encoursFinancier; // (traiteEnCoffre + chequeEnCoffre)
}
