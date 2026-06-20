package com.reapro.achat.Controller;

import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.DTO.articlemanagement.ArticleExtraResponse;
import com.reapro.achat.DTO.articlemanagement.ArticleManagementItemResponse;
import com.reapro.achat.services.ArticleManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Page « Gestion Articles » — consultation paginée (V1). Authentifié (règle globale). Pas de CRUD.
 *
 * Liste rapide via cache PostgreSQL + temps réel SQL Server (stock/qté import/prix).
 *  GET /api/articles-management/items                  → liste paginée
 *  GET /api/articles-management/items/{itemNo}/extra   → données lourdes du dialog (dernier achat)
 */
@RestController
@RequestMapping("/api/articles-management")
@RequiredArgsConstructor
public class ArticleManagementController {

    private final ArticleManagementService service;

    @GetMapping("/items")
    public PagedResponse<ArticleManagementItemResponse> getItems(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String manufacturerCode,
            @RequestParam(required = false) String groupCode,
            @RequestParam(required = false) String subGroupCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return service.getArticles(search, manufacturerCode, groupCode, subGroupCode, page, size);
    }

    /** Données chargées à l'ouverture du dialog détail (hors liste pour la performance). */
    @GetMapping("/items/{itemNo}/extra")
    public ArticleExtraResponse getExtra(@PathVariable String itemNo) {
        return service.getExtra(itemNo);
    }
}
