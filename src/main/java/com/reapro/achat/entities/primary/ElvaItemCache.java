package com.reapro.achat.entities.primary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import lombok.Data;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;

@Entity
@Table(name = "elva_item_cache")
@Data
public class ElvaItemCache implements Persistable<String> {

    @Id
    @Column(name = "no", length = 100)
    private String no;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "search_description", length = 500)
    private String searchDescription;

    @Column(name = "description_2", length = 500)
    private String description2;

    @Column(name = "base_unit_of_measure", length = 50)
    private String baseUnitOfMeasure;

    @Column(name = "type")
    private Integer type;

    @Column(name = "inventory_posting_group", length = 100)
    private String inventoryPostingGroup;

    @Column(name = "unit_price", precision = 18, scale = 5)
    private BigDecimal unitPrice;

    @Column(name = "unit_cost", precision = 18, scale = 5)
    private BigDecimal unitCost;

    @Column(name = "last_direct_cost", precision = 18, scale = 5)
    private BigDecimal lastDirectCost;

    @Column(name = "vendor_no", length = 100)
    private String vendorNo;

    @Column(name = "vendor_item_no", length = 100)
    private String vendorItemNo;

    @Column(name = "blocked")
    private Integer blocked;

    @Column(name = "item_category_code", length = 100)
    private String itemCategoryCode;

    @Column(name = "make_code", length = 100)
    private String makeCode;

    @Column(name = "description_structuree", length = 500)
    private String descriptionStructuree;

    @Column(name = "item_product_code", length = 100)
    private String itemProductCode;

    @Column(name = "item_sub_product_code", length = 100)
    private String itemSubProductCode;

    @Column(name = "groupe", length = 200)
    private String groupe;

    @Column(name = "sous_groupe", length = 200)
    private String sousGroupe;

    @Column(name = "reference_origine_lie", length = 200)
    private String referenceOrigineLie;

    @Column(name = "code_fabricant", length = 100)
    private String codeFabricant;

    @Column(name = "fabricant", length = 200)
    private String fabricant;

    @Column(name = "tecdoc_id_fabricant", length = 100)
    private String tecdocIdFabricant;

    @Column(name = "is_oem", length = 50)
    private String isOem;

    @Column(name = "id", length = 150)
    private String id;

    @Column(name = "produit", length = 100)
    private String produit;

    @Column(name = "is_kit", length = 50)
    private String isKit;

    @Column(name = "have_info", length = 50)
    private String haveInfo;

    @Column(name = "champs_libre", length = 500)
    private String champsLibre;

    @Transient
    private boolean isNew = true;

    @Override
    public String getId() {
        return no;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    protected void markNotNew() {
        this.isNew = false;
    }
}
