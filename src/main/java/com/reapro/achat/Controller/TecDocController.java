package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ArticleVerificationResponse;
import com.reapro.achat.DTO.tecdoc.TecDocApiResponse;
import com.reapro.achat.DTO.tecdoc.TecDocCatalogQuery;
import com.reapro.achat.DTO.tecdoc.TecDocVehicleDetail;
import com.reapro.achat.services.ArticleVerificationService;
import com.reapro.achat.services.TecDocService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

// RBAC Lot 4 : page Catalogue TecDoc complète → TECDOC_CATALOG_ACCESS (super-admin bypass via authorities).
@RestController
@RequestMapping("/api/tecdoc")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('TECDOC_CATALOG_ACCESS')")
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


    @GetMapping("/article-vehicles")
    public List<TecDocVehicleDetail> getArticleVehicles(
            @RequestParam Long articleId,
            @RequestParam Long manuId) {
        return tecDocService.getLinkedVehiclesDetails(articleId, manuId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CATALOGUE TECDOC (Phase 1) — endpoints destinés à la future page « Catalogue TecDoc ».
    // articleCountry = "TN" et lang = "fr" sont appliqués côté service (jamais reçus du front).
    // Aucun endpoint VIN. Tous protégés par défaut (Spring Security : anyRequest().authenticated()).
    // ─────────────────────────────────────────────────────────────────────────

    /** Autocomplétion de la barre de recherche catalogue. */
    @GetMapping("/suggestions")
    public Map<String, Object> getSuggestions(@RequestParam("query") String query) {
        return tecDocService.getAutoCompleteSuggestions(query);
    }

    /** Étape 1 véhicule : constructeurs. */
    @GetMapping("/vehicles/manufacturers")
    public Map<String, Object> getVehicleManufacturers(
            @RequestParam(value = "type", defaultValue = "P") String type) {
        return tecDocService.getVehicleManufacturers(type);
    }

    /** Étape 2 véhicule : modèles / séries d'un constructeur. */
    @GetMapping("/vehicles/models")
    public Map<String, Object> getVehicleModels(
            @RequestParam(value = "type", defaultValue = "P") String type,
            @RequestParam("mfrId") Long mfrId) {
        return tecDocService.getVehicleModels(type, mfrId);
    }

    /** Étape 3 véhicule : types / motorisations d'une série de modèle. */
    @GetMapping("/vehicles/types")
    public Map<String, Object> getVehicleTypes(
            @RequestParam(value = "type", defaultValue = "P") String type,
            @RequestParam("mfrId") Long mfrId,
            @RequestParam("modelSeriesId") Long modelSeriesId,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPage", defaultValue = "50") int perPage) {
        return tecDocService.getVehicleTypes(type, mfrId, modelSeriesId, clampPage(page), clampPerPage(perPage));
    }

    /** Liste globale des fabricants/fournisseurs TecDoc (référence, non scopée à une recherche). */
    @GetMapping("/data-suppliers")
    public List<Map<String, Object>> getDataSuppliers() {
        return tecDocService.getDataSuppliers();
    }

    /** Familles / sous-familles (arbre assembly groups) pour un véhicule. */
    @GetMapping("/assembly-groups")
    public Map<String, Object> getAssemblyGroups(
            @RequestParam("linkageTargetId") Long linkageTargetId,
            @RequestParam(value = "linkageTargetType", defaultValue = "P") String linkageTargetType) {
        return tecDocService.getAssemblyGroups(linkageTargetId, linkageTargetType);
    }

    /** Recherche catalogue d'articles (texte / famille / véhicule) paginée. */
    @GetMapping("/catalog/articles")
    public Map<String, Object> getCatalogArticles(
            @RequestParam(value = "searchQuery", required = false) String searchQuery,
            @RequestParam(value = "searchType", required = false) Integer searchType,
            @RequestParam(value = "searchMatchType", required = false) String searchMatchType,
            @RequestParam(value = "assemblyGroupNodeId", required = false) Long assemblyGroupNodeId,
            @RequestParam(value = "linkageTargetId", required = false) Long linkageTargetId,
            @RequestParam(value = "linkageTargetType", required = false) String linkageTargetType,
            @RequestParam(value = "dataSupplierIds", required = false) Integer dataSupplierIds,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPage", defaultValue = "50") int perPage) {

        TecDocCatalogQuery query = new TecDocCatalogQuery(
                searchQuery, searchType, searchMatchType,
                assemblyGroupNodeId, linkageTargetId, linkageTargetType,
                dataSupplierIds, clampPage(page), clampPerPage(perPage));
        return tecDocService.getCatalogArticles(query);
    }

    private static int clampPage(int page) {
        return Math.max(page, 1);
    }

    private static int clampPerPage(int perPage) {
        if (perPage < 1) return 50;
        return Math.min(perPage, 100);
    }
}