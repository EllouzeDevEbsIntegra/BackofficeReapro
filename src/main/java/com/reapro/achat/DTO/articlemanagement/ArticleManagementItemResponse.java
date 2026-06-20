package com.reapro.achat.DTO.articlemanagement;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ligne d'article pour la page « Gestion Articles » (consultation V1).
 * Montants en String (précision JSON) — convention {@code ElvaItemResponse}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArticleManagementItemResponse {

    private String itemNo;
    private String description;
    private String descriptionStructured;
    private String groupCode;
    private String groupName;
    private String subGroupCode;
    private String subGroupName;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal inventory;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal unitPrice;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal qtyImport;

    private String masterReference;
    private long oemCount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal unitCost;

    /** Dernier achat — enrichi depuis la vue LastInvoicedItemCost (null si aucun). */
    private LocalDate lastPurchaseDate;

    /** Total vente — non disponible dans ELVA_Item en V1 (toujours null, documenté). */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal totalSales;

    private String manufacturerCode;
    private String manufacturerName;
    private String vendorNo;

    // ── Champs complémentaires (affichés dans le dialog détail, pas dans la liste) ──
    private String makeCode;
    private String tecdocIdFabricant;
    private String vendorItemNo;
}
