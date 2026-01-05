package com.reapro.achat.Controller;

import com.reapro.achat.DTO.bc.QuoteLineBC;
import com.reapro.achat.DTO.bc.QuoteLineUpdateRequest;
import com.reapro.achat.services.QuoteLineBCService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bc/quote-lines")
@RequiredArgsConstructor
public class QuoteLineBCController {

    private final QuoteLineBCService service;

    /**
     * GET : liste des lignes de devis filtrées par CompareQuoteNo + ReferenceMaster
     */
    @GetMapping
    public List<QuoteLineBC> getQuoteLines(
            @RequestParam String compareQuoteNo,
            @RequestParam String referenceMaster,
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId
    ) {
        return service.getQuoteLines(companyId, compareQuoteNo, referenceMaster);
    }

    /**
     * PATCH : mise à jour d'une ligne de devis.
     *
     * Le front fournit :
     *   - l'id de la ligne (path variable)
     *   - le body JSON avec les champs à modifier
     * Le backend s'occupe de :
     *   - récupérer l'ETag actuel auprès de BC
     *   - appeler PATCH BC avec If-Match
     */
    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateQuoteLine(
            @PathVariable String id,
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId,
            @RequestBody QuoteLineUpdateRequest request
    ) {
        service.updateQuoteLine(companyId, id, request);
    }
}