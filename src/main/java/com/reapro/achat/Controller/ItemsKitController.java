package com.reapro.achat.Controller;

import com.reapro.achat.DTO.BcItemEnrichedResponse;
import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.services.ItemsKitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/itemsKit")
@RequiredArgsConstructor
public class ItemsKitController {

    private final ItemsKitService service;

    /**
     * Exemple:
     * GET /api/itemsKit?no=39613&page=0&size=20
     */
    @GetMapping
    public PagedResponse<BcItemEnrichedResponse> getItemsKit(
            @AuthenticationPrincipal String email,
            @RequestParam String no,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String compareQuoteNo,
            @RequestParam(required = false) String stockOperator,
            @RequestParam(required = false) BigDecimal stockValue,
            @RequestParam(required = false) String dateDernierAchatOperator,
            @RequestParam(required = false) String dateDernierAchatValue,
            @RequestParam(required = false) String referenceOperator,
            @RequestParam(required = false) String referenceValue
    ) {
        return service.getItemsKit(email, no, page, size, compareQuoteNo,
                stockOperator, stockValue, dateDernierAchatOperator, dateDernierAchatValue,
                referenceOperator, referenceValue);
    }
}
