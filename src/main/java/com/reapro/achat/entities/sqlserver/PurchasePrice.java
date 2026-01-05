package com.reapro.achat.entities.sqlserver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "View_ProjectReapro_PurchasePrices", schema = "dbo")
@Immutable // vue => lecture seule
@Data
public class PurchasePrice {

    @Id
    @Column(name = "[$systemId]")
    private String systemId;

    @Column(name = "[Vendor No_]")
    private String vendorNo;

    @Column(name = "[Item No_]")
    private String itemNo;

    @Column(name = "[Currency Code]")
    private String currencyCode;

    @Column(name = "[Direct Unit Cost]")
    private BigDecimal directUnitCost;

    @Column(name = "[Starting Date]")
    private LocalDate startingDate;

    @Column(name = "[Ending Date]")
    private LocalDate endingDate;
}