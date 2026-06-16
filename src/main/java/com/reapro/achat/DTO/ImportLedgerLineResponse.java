// src/main/java/com/reapro/achat/DTO/ImportLedgerLineResponse.java

package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ligne "Import" (écriture item-ledger en magasin d'import, quantité restante > 0)
 * pour un article + fournisseur. Colonnes exposées au dialog Lignes Import C2.
 */
@Data
@AllArgsConstructor
public class ImportLedgerLineResponse {

    private String documentNo;
    private String sourceNo;
    private String sourceName;
    private String locationCode;
    private LocalDate postingDate;
    private BigDecimal quantity;
    private BigDecimal remainingQuantity;
    private BigDecimal costAmountExpected;
}
