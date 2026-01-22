package com.reapro.achat.DTO;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ElvaItemResponse {
    private String no;
    private String description;
    private String searchDescription;
    private String description2;
    private String baseUnitOfMeasure;
    private Integer type;
    private String inventoryPostingGroup;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal unitPrice;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal unitCost;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal lastDirectCost;

    private String vendorNo;
    private String vendorItemNo;
    private Integer blocked;
    private String itemCategoryCode;
    private String makeCode;
    private String descriptionStructuree;
    private String itemProductCode;
    private String itemSubProductCode;
    private String groupe;
    private String sousGroupe;
    private String referenceOrigineLie;
    private String codeFabricant;
    private String fabricant;
    private String tecdocIdFabricant;
    private String isOem;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal quantite;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal reservedQuantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal receptionQty;

    private String id;
    private String produit;
    private String isKit;
    private String haveInfo;
    private String champsLibre;
}
