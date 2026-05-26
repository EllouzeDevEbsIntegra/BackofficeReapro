package com.reapro.achat.entities.sqlserver;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "ELVA_sales_price", schema = "dbo")
@Immutable
@Data
@IdClass(ElvaSalesPrice.ElvaSalesPriceId.class) // Composite Primary Key
public class ElvaSalesPrice {

    @Id
    @Column(name = "Item No_")
    private String itemNo;

    @Id
    @Column(name = "Sales Code")
    private String salesCode; // This is the clientId

    @Id
    @Column(name = "Starting Date")
    private LocalDateTime startingDate;

    @Column(name = "Unit Price")
    private BigDecimal unitPrice;

    @Column(name = "Ending Date")
    private LocalDateTime endingDate;

    // Composite Key Class
    public static class ElvaSalesPriceId implements Serializable {
        private String itemNo;
        private String salesCode;
        private LocalDateTime startingDate;
    }
}
