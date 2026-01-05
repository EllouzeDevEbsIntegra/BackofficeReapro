package com.reapro.achat.Controller;

import com.reapro.achat.entities.sqlserver.PurchasePrice;
import com.reapro.achat.services.PurchasePriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-prices")
@RequiredArgsConstructor
public class PurchasePriceController {

    private final PurchasePriceService service;

    @GetMapping
    public List<PurchasePrice> getPurchasePrices(
            @RequestParam String itemNo,
            @RequestParam(required = false) String vendorNo  // ✅ plus obligatoire
    ) {
        return service.getPrices(vendorNo, itemNo);
    }
}