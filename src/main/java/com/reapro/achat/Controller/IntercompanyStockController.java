package com.reapro.achat.Controller;

import com.reapro.achat.DTO.IntercompanyStockResponse;
import com.reapro.achat.services.IntercompanyStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bc/items")
@RequiredArgsConstructor
public class IntercompanyStockController {

    private final IntercompanyStockService service;

    /**
     * Retourne le stock des autres sociétés (exclut la société du profil)
     * GET /api/bc/items/intercompany-stock?no=LX804/S
     */
    @GetMapping("/intercompany-stock")
    public List<IntercompanyStockResponse> getIntercompanyStock(
            @AuthenticationPrincipal String email,
            @RequestParam String no
    ) {
        return service.getIntercompanyStockExcludingMyCompany(email, no);
    }
}