package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class QuoteLineBC {

    @JsonProperty("id")
    private String id;

    @JsonProperty("@odata.etag")
    private String etag;

    @JsonProperty("documentNo")
    private String documentNo;

    @JsonProperty("lineNo")
    private Integer lineNo;

    @JsonProperty("CompareQuoteNo")
    private String compareQuoteNo;

    @JsonProperty("ReferenceMaster")
    private String referenceMaster;

    @JsonProperty("buyFromVendorNo")
    private String buyFromVendorNo;

    @JsonProperty("no")
    private String no;

    @JsonProperty("VendorItemNo")
    private String vendorItemNo;

    @JsonProperty("ManufacturerTecdocId")
    private String manufacturerTecdocId;

    @JsonProperty("descriptionStructured")
    private String descriptionStructured;

    @JsonProperty("unitPriceLCY")
    private BigDecimal unitPriceLCY;

    @JsonProperty("initialQuantity")
    private BigDecimal initialQuantity;

    @JsonProperty("vendorQuantity")
    private BigDecimal vendorQuantity;

    @JsonProperty("quantity")
    private BigDecimal quantity;

    @JsonProperty("treated")
    private Boolean treated;

    @JsonProperty("gapUnitCost")
    private BigDecimal gapUnitCost;

    @JsonProperty("preferential")
    private Boolean preferential;

    @JsonProperty("directUnitCost")
    private BigDecimal directUnitCost;

    @JsonProperty("LastDirectCost")
    private BigDecimal lastDirectCost;

    @JsonProperty("initialVendorPrice")
    private BigDecimal initialVendorPrice;

    @JsonProperty("prixDeRevientCalcule")
    private BigDecimal prixDeRevientCalcule;

    @JsonProperty("importInventory")
    private BigDecimal importInventory;

    @JsonProperty("inventoryWithoutImport")
    private BigDecimal inventoryWithoutImport;

    @JsonProperty("qtyOnPurchOrder")
    private BigDecimal qtyOnPurchOrder;

    @JsonProperty("lastDirectUnitCostCalculated")
    private BigDecimal lastDirectUnitCostCalculated;

    @JsonProperty("calcAncienPrixDeVente")
    private BigDecimal calcAncienPrixDeVente;

    @JsonProperty("availableInventory")
    private BigDecimal availableInventory;

    @JsonProperty("dateDernierAchat")
    private LocalDate dateDernierAchat;

    @JsonProperty("quoteLineReason")
    private String quoteLineReason;

    @JsonProperty("QuoteLineComment")
    private String quoteLineComment;

    @JsonProperty("askingPrice")
    private BigDecimal askingPrice;

    @JsonProperty("askingQty")
    private BigDecimal askingQty;

    @JsonProperty("styleQty")
    private String styleQty;

    @JsonProperty("styleInvImport")
    private String styleInvImport;

    @JsonProperty("styleInvNoImport")
    private String styleInvNoImport;

    @JsonProperty("styleDate")
    private String styleDate;


}
