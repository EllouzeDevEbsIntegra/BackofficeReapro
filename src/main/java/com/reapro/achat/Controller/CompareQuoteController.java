// src/main/java/com/reapro/achat/Controller/CompareQuoteController.java

package com.reapro.achat.Controller;

import com.reapro.achat.entities.sqlserver.CompareQuote;
import com.reapro.achat.services.CompareQuoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/compare-quotes")
@RequiredArgsConstructor
public class CompareQuoteController {

    private final CompareQuoteService service;

    @GetMapping
    public Page<CompareQuote> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {

        PageRequest pr = PageRequest.of(page, size);
        return service.search(search, pr);
    }

    @GetMapping("/{no}")
    public CompareQuote getByNo(@PathVariable String no) {
        return service.getByNo(no);
    }
}