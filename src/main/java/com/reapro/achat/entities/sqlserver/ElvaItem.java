package com.reapro.achat.entities.sqlserver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Entity
@Table(name = "ELVA_Item", schema = "dbo")
@Immutable // Assuming this is read-only like the view example
@Data
public class ElvaItem {

    // Using 'No_' as the ID based on typical BC/NAV structures. 
    // If 'Id' is the actual primary key, this should be changed.
    @Id
    @Column(name = "No_")
    private String no;

    @Column(name = "Description")
    private String description;

    @Column(name = "Search Description")
    private String searchDescription;

    @Column(name = "Description 2")
    private String description2;

    @Column(name = "Base Unit of Measure")
    private String baseUnitOfMeasure;

    @Column(name = "Type")
    private Integer type; // Keep as Integer, as 0 is a valid integer

    @Column(name = "Inventory Posting Group")
    private String inventoryPostingGroup;

    @Column(name = "Unit Price")
    private BigDecimal unitPrice; // Keep as BigDecimal, 0E-20 is a valid BigDecimal representation of a very small number

    @Column(name = "Unit Cost")
    private BigDecimal unitCost; // Keep as BigDecimal

    @Column(name = "Last Direct Cost")
    private BigDecimal lastDirectCost; // Keep as BigDecimal

    @Column(name = "Vendor No_")
    private String vendorNo;

    @Column(name = "Vendor Item No_")
    private String vendorItemNo;

    @Column(name = "Blocked")
    private Integer blocked; // Keep as Integer, as 0 is a valid integer

    @Column(name = "Item Category Code")
    private String itemCategoryCode;

    @Column(name = "Make Code")
    private String makeCode;

    @Column(name = "Description structurée")
    private String descriptionStructuree;

    @Column(name = "Item Product Code")
    private String itemProductCode;

    @Column(name = "Item Sub Product Code")
    private String itemSubProductCode;

    @Column(name = "Groupe")
    private String groupe;

    @Column(name = "Sous Groupe")
    private String sousGroupe;

    @Column(name = "Reference Origine Lié")
    private String referenceOrigineLie;

    @Column(name = "code Fabricant")
    private String codeFabricant;

    @Column(name = "Fabricant")
    private String fabricant;

    @Column(name = "Tecdoc id fabricant")
    private String tecdocIdFabricant; // Changed from Integer to String based on JSON example "100002"

    @Column(name = "isOEM")
    private String isOem; // Changed from Integer to String based on JSON example "1"

    @Column(name = "Quantité")
    private BigDecimal quantite; // Keep as BigDecimal, but it can be null

    @Column(name = "ReservedQuantity")
    private BigDecimal reservedQuantity; // Keep as BigDecimal

    @Column(name = "reception_qty")
    private BigDecimal receptionQty; // Keep as BigDecimal

    @Column(name = "Id")
    private String id; // The GUID from BC

    @Column(name = "Produit")
    private String produit; // Keep as String, as "0" is a valid string

    @Column(name = "isKit")
    private String isKit; // Changed from Integer to String based on JSON example "NON"

    @Column(name = "HaveInfo")
    private String haveInfo; // Changed from Integer to String based on JSON example "0"

    @Column(name = "Champs libre")
    private String champsLibre;
}
