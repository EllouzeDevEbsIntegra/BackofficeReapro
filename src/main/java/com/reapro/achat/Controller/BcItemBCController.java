package com.reapro.achat.Controller;

import com.reapro.achat.DTO.BcItemEnrichedResponse;
import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.services.BcItemBCService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bc/itemsEqv")
@RequiredArgsConstructor
public class BcItemBCController {

    private final BcItemBCService service;

    @GetMapping
    public PagedResponse<BcItemEnrichedResponse> getItems(
            @RequestParam String referenceMaster,
            @RequestParam String no,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId,
            @RequestParam(required = false) String compareQuoteNo
    ) {
        return service.getItemsByReferenceAndNotNoSortedLocally(companyId, referenceMaster, no, page, size, compareQuoteNo);
    }
}
