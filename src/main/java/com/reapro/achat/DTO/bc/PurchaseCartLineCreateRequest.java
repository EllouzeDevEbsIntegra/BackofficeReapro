package com.reapro.achat.DTO.bc;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class PurchaseCartLineCreateRequest {
    private String buyFromVendorNo;
    private String itemNo;
    private String refMaster;
    private BigDecimal quantity;
    private BigDecimal directUnitCost;
    private String compareQuoteNo;
    private String comment;
}
