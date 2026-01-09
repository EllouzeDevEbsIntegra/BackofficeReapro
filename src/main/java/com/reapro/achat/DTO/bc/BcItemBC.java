package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcItemBC {

    @JsonProperty("@odata.etag")
    private String etag;

    @JsonProperty("id")
    private String id;

    @JsonProperty("vendorNo")
    private String vendorNo;

    @JsonProperty("VendorItemNo")
    private String vendorItemNo;

    @JsonProperty("manufacturerTecdocId")
    private String manufacturerTecdocId;

    @JsonProperty("no")
    private String no;

    @JsonProperty("ReferenceMaster")
    private String referenceMaster;

    @JsonProperty("descriptionStructured")
    private String descriptionStructured;

    @JsonProperty("qtyStock")
    private BigDecimal qtyStock;

    @JsonProperty("qtyImport")
    private BigDecimal qtyImport;

    @JsonProperty("qtyOnPurchOrder")
    private BigDecimal qtyOnPurchOrder;

    @JsonProperty("totalVendu")
    private BigDecimal totalVendu;

    @JsonProperty("totalAchete")
    private BigDecimal totalAchete;

    @JsonProperty("venduCurrYear")
    private BigDecimal venduCurrYear;

    @JsonProperty("acheteCurrYear")
    private BigDecimal acheteCurrYear;

    @JsonProperty("lastPurshCostDS")
    private BigDecimal lastPurshCostDS;

    @JsonProperty("lastPurshDate")
    private LocalDate lastPurshDate;

    @JsonProperty("LastPreferential")
    private Boolean lastPreferential;

    @JsonProperty("unitPrice")
    private BigDecimal unitPrice;

    @JsonProperty("lastCurrPrice")
    private BigDecimal lastCurrPrice;

    @JsonProperty("lastDate")
    private LocalDate lastDate;

    @JsonProperty("styleQty")
    private String styleQty;

    @JsonProperty("styleImportQty")
    private String styleImportQty;

    @JsonProperty("styleOnPurchQty")
    private String styleOnPurchQty;


}
