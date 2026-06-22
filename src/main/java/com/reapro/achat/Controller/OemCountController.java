package com.reapro.achat.Controller;

import com.reapro.achat.services.OemCountCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

// RBAC Lot 4bis-B : donnée Info Article transversale → lecture si Info Article OU module consommateur.
@RestController
@RequestMapping("/api/oem-count")
@PreAuthorize("hasAnyAuthority('ARTICLE_INFO_READ','COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS','B2B_ACCESS','ARTICLE_MANAGEMENT_ACCESS','TECDOC_CATALOG_ACCESS')")
public class OemCountController {

    private final OemCountCalculationService oemCountCalculationService;

    public OemCountController(OemCountCalculationService oemCountCalculationService) {
        this.oemCountCalculationService = oemCountCalculationService;
    }

    @GetMapping("/calculate/{masterItemNo}")
    public Mono<ResponseEntity<Integer>> getCalculatedOemCount(@PathVariable String masterItemNo) {
        return oemCountCalculationService.calculateOemCount(masterItemNo)
                .map(count -> {
                    if (count == null || count == 0) {
                        return ResponseEntity.noContent().build();
                    }
                    return ResponseEntity.ok(count);
                });
    }
}
