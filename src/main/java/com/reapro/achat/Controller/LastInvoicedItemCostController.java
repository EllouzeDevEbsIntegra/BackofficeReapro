package com.reapro.achat.Controller;

import com.reapro.achat.entities.sqlserver.LastInvoicedItemCost;
import com.reapro.achat.services.LastInvoicedItemCostService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/last-invoiced-cost")
@RequiredArgsConstructor
public class LastInvoicedItemCostController {

    private final LastInvoicedItemCostService service;


    @GetMapping
    public List<LastInvoicedItemCost> getLastInvoicedCost(@RequestParam String itemNo) {
        return service.getByItemNo(itemNo);
    }
}