package com.reapro.achat.entities.primary;

import com.reapro.achat.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sales_order_lines", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"sales_order_id", "itemReference"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sales_order_id")
    private SalesOrder salesOrder;

    @Column(nullable = false)
    private String itemReference;

    private String designation;
    private String brand;
    private String unit;

    @Column(nullable = false)
    private int quantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal unitPrice;

    private int availableStock;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    private String businessCentralLineNumber;
    private String businessCentralSystemId;

    @Column(precision = 19, scale = 4)
    private BigDecimal discountPercent;

    @Column(precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal amountExcludingTax;

    @Column(precision = 19, scale = 4)
    private BigDecimal vatPercent;

    @Column(precision = 19, scale = 4)
    private BigDecimal vatAmount;

    @Column(precision = 19, scale = 4)
    private BigDecimal amountIncludingTax;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = OrderStatus.DRAFT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
