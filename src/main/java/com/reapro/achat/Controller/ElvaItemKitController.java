package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ElvaItemKitResponse;
import com.reapro.achat.services.ElvaItemKitService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/item-kits")
@RequiredArgsConstructor
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