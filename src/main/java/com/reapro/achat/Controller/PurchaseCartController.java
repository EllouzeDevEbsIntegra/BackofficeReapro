package com.reapro.achat.Controller;

import com.reapro.achat.DTO.bc.PurchaseCartLineBC;
import com.reapro.achat.DTO.bc.PurchaseCartLineCreateRequest;
import com.reapro.achat.DTO.bc.PurchaseCartLineUpdateRequest;
import com.reapro.achat.services.PurchaseCartService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/bc/purchase-cart")
@RequiredArgsConstructor
public class PurchaseCartController {

    private final PurchaseCartService purchaseCartService;

    @GetMapping
    public ResponseEntity<Page<PurchaseCartLineBC>> getPurchaseCartLines(
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId,
            @RequestParam(required = false) String compareQuoteNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String vendorNo,
            @RequestParam(required = false) String itemNo, // Added itemNo
            Pageable pageable) {

        Page<PurchaseCartLineBC> response = purchaseCartService.getPurchaseCartLines(companyId, compareQuoteNo, status, vendorNo, itemNo, pageable);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getPurchaseCartLinesCount(
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId,
            @RequestParam String compareQuoteNo,
            @RequestParam(required = false) String itemNo) {

        long count = purchaseCartService.getPurchaseCartLinesCount(companyId, compareQuoteNo, itemNo);
        return ResponseEntity.ok(Map.of("count", count));
    }

    @PostMapping
    public ResponseEntity<PurchaseCartLineBC> addPurchaseCartLine(
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId,
            @RequestBody PurchaseCartLineCreateRequest request) {

        PurchaseCartLineBC response = purchaseCartService.addPurchaseCartLine(companyId, request);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{lineNo}")
    public ResponseEntity<PurchaseCartLineBC> updatePurchaseCartLine(
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId,
            @PathVariable Integer lineNo,
            @RequestBody PurchaseCartLineUpdateRequest request) {

        PurchaseCartLineBC response = purchaseCartService.updatePurchaseCartLine(companyId, lineNo, request);
        return ResponseEntity.ok(response);
    }
}
