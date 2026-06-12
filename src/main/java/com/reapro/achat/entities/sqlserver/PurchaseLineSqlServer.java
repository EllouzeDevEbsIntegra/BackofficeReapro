package com.reapro.achat.entities.sqlserver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "View_ProjectReapro_PurchaseLinesCmd")
@Immutable // Mark as immutable since it's a view
@Data
public class PurchaseLineSqlServer {

    @Id
    @Column(name = "Document No_")
    private String documentNo;

    @Column(name = "Buy-from Vendor No_")
    private String buyFromVendorNo;

    @Column(name = "No_")
    private String no;

    @Column(name = "Location Code")
    private String locationCode;

    @Column(name = "Order Date")
    private LocalDateTime orderDate;

    @Column(name = "Description")
    private String description;

    @Column(name = "Quantity")
    private BigDecimal quantity;

    @Column(name = "Outstanding Quantity")
    private BigDecimal outstandingQuantity;

    @Column(name = "Qty First Confirmation")
    private BigDecimal qtyFirstConfirmation;
}
