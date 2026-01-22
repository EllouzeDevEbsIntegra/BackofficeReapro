package com.reapro.achat.Controller;

import com.reapro.achat.services.OemCountCalculationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/oem-count")
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
