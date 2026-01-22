package com.reapro.achat.Controller;

import com.reapro.achat.DTO.salesorder.*;
import com.reapro.achat.services.SalesOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    // --- PANIER ACTIF (BROUILLON) ---

    @GetMapping("/active")
    public SalesOrderResponse getActiveCart(
            @AuthenticationPrincipal String email,
            @RequestParam String clientId) {
        return salesOrderService.getActiveCart(email, clientId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesOrderResponse createCart(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody CreateSalesOrderRequest request) {
        return salesOrderService.createCart(email, request);
    }

    // --- LIGNES DE PANIER ---

    @PostMapping("/{orderId}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    public SalesOrderResponse addLineToCart(
            @AuthenticationPrincipal String email,
            @PathVariable Long orderId,
            @Valid @RequestBody AddSalesOrderLineRequest request) {
        return salesOrderService.addLineToCart(email, orderId, request);
    }

    @PatchMapping("/{orderId}/lines/{lineId}")
    public SalesOrderResponse updateLineQuantity(
            @AuthenticationPrincipal String email,
            @PathVariable Long orderId,
            @PathVariable Long lineId,
            @Valid @RequestBody UpdateSalesOrderLineQuantityRequest request) {
        return salesOrderService.updateLineQuantity(email, orderId, lineId, request);
    }

    @DeleteMapping("/{orderId}/lines/{lineId}")
    public SalesOrderResponse deleteLine(
            @AuthenticationPrincipal String email,
            @PathVariable Long orderId,
            @PathVariable Long lineId) {
        return salesOrderService.deleteLine(email, orderId, lineId);
    }

    // --- HISTORIQUE ---

    @GetMapping
    public Page<SalesOrderResponse> getOrderHistory(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false) String clientId,
            @RequestParam(required = false) com.reapro.achat.enums.OrderStatus status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime dateFrom,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime dateTo,
            Pageable pageable) {
        return salesOrderService.getOrderHistory(email, clientId, status, dateFrom, dateTo, pageable);
    }

    @GetMapping("/{orderId}")
    public SalesOrderResponse getOrderById(
            @AuthenticationPrincipal String email,
            @PathVariable Long orderId) {
        return salesOrderService.getOrderById(email, orderId);
    }

    // --- VALIDATION ---

    @PostMapping("/{orderId}/validate")
    public Object validateOrder(
            @AuthenticationPrincipal String email,
            @PathVariable Long orderId) {
        return salesOrderService.validateOrder(email, orderId);
    }
}
