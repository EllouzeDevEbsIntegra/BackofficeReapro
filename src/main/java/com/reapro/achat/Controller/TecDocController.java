package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ArticleVerificationResponse;
import com.reapro.achat.DTO.tecdoc.TecDocApiResponse;
import com.reapro.achat.services.ArticleVerificationService;
import com.reapro.achat.services.TecDocService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tecdoc")
@RequiredArgsConstructor
public class TecDocController {

    private final TecDocService tecDocService;
    private final ArticleVerificationService verificationService;

    /**
     * Recherche d'articles Standard (Type 0)
     * GET /api/tecdoc/articles?search=02380&supplier=101
     */
    @GetMapping("/articles")
    public TecDocApiResponse searchArticles(
            @RequestParam("search") String searchQuery,
            @RequestParam(value = "supplier", required = false) Integer supplierId
    ) {
        return tecDocService.searchArticles(searchQuery, supplierId);
    }

    /**
     * Vérification Création Article (Cross-check TecDoc vs BC)
     * GET /api/tecdoc/verify?query=6511800109
     *
     * 1. Appelle TecDoc (Type 1)
     * 2. Filtre par Fabricants existants dans BC
     * 3. Vérifie si l'article (VendorItemNo) existe déjà dans BC
     */
    @GetMapping("/verify")
    public ArticleVerificationResponse verifyArticle(
            @AuthenticationPrincipal String email,
            @RequestParam("query") String searchQuery
    ) {
        return verificationService.verifyArticles(email, searchQuery);
    }
}