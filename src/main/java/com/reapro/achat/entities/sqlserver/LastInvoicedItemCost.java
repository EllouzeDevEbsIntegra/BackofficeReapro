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
@Table(name = "View_ProjectReapro_LastInvoicedItemCost", schema = "dbo")
@Immutable
@Data
public class LastInvoicedItemCost {

    @Id
    @Column(name = "SystemId") // <-- si ta colonne s'appelle exactement SystemId
    private String systemId;

    @Column(name = "Frs")
    private String frs;

    @Column(name = "Ref_Frs")
    private String refFrs;

    @Column(name = "No_")
    private String no;

    @Column(name = "LastInvoicedDirectCost")
    private BigDecimal lastInvoicedDirectCost;

    @Column(name = "Quantity")
    private BigDecimal quantity;

    @Column(name = "LastInvoicedCostDate")
    private LocalDate lastInvoicedCostDate;
}