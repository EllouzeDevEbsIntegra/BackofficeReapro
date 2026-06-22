package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ElvaItemKitResponse;
import com.reapro.achat.services.ElvaItemKitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// RBAC Lot 4bis-B : kits (Info Article) → lecture si Info Article OU module consommateur.
@RestController
@RequestMapping("/api/item-kits")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ARTICLE_INFO_READ','COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS','B2B_ACCESS','ARTICLE_MANAGEMENT_ACCESS','TECDOC_CATALOG_ACCESS')")
public class ElvaItemKitController {

    private final ElvaItemKitService service;

    /**
     * Exemple:
     * GET /api/item-kits?article=39613
     */
    @GetMapping
    public List<ElvaItemKitResponse> getByArticle(@RequestParam String article) {
        return service.getByArticle(article);
    }
}