package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ElvaItemKitResponse {
    private String article;
    private String description;
    private String itemKit;
    private String descriptionItemKit;
    private String kit;
    
    private Boolean existPurchaseCart;
    private String commentPurchaseCart;
}
