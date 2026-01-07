package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PurchaseCartLineBC {

    @JsonProperty("@odata.etag")
    private String etag;

    @JsonProperty("lineNo")
    private Integer lineNo;

    @JsonProperty("buyFromVendorNo")
    private String buyFromVendorNo;

    @JsonProperty("itemNo")
    private String itemNo;

    @JsonProperty("refMaster")
    private String refMaster;

    @JsonProperty("description")
    private String description;

    @JsonProperty("comment")
    private String comment;

    @JsonProperty("quantity")
    private BigDecimal quantity;

    @JsonProperty("directUnitCost")
    private BigDecimal directUnitCost;

    @JsonProperty("compareQuoteNo")
    private String compareQuoteNo;

    @JsonProperty("addedDate")
    private LocalDate addedDate;

    @JsonProperty("status")
    private String status;

    @JsonProperty("purchaseQuoteNo")
    private String purchaseQuoteNo;
}
