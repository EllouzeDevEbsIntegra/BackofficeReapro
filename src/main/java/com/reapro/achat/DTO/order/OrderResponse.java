package com.reapro.achat.DTO.order;

import com.reapro.achat.enums.OrderStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class OrderResponse {
    private Long id;
    private String localNumber;
    private OrderStatus status;
    private String clientId;
    private LocalDateTime createdAt;
    private String createdBy; // User's full name or email
    private LocalDateTime validatedAt;
    private String validatedBy;
    private String businessCentralOrderNumber;
    private String businessCentralSystemId;
    private BigDecimal totalExcludingTax;
    private BigDecimal totalTax;
    private BigDecimal totalIncludingTax;
    private BigDecimal totalDiscount;
    private List<OrderLineResponse> lines;
}
