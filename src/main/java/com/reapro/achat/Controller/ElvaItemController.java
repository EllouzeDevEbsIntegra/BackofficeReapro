package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ElvaItemResponse;
import com.reapro.achat.DTO.ElvaItemSearchRequest;
import com.reapro.achat.services.ElvaItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// RBAC Lot 4bis-B : lectures catalogue Elva (top-1000 / search / équivalences / kits) = données Info Article
// transversales → lecture si Info Article OU module consommateur. Le POST /sync conserve son @PreAuthorize
// méthode ADAPTABLE_SYNC_RUN (l'annotation de méthode prime sur celle de classe).
@RestController
@RequestMapping("/api/elva-items")
@RequiredArgsConstructor
@org.springframework.validation.annotation.Validated
@PreAuthorize("hasAnyAuthority('ARTICLE_INFO_READ','COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS','B2B_ACCESS','ARTICLE_MANAGEMENT_ACCESS','TECDOC_CATALOG_ACCESS')")
public class ElvaItemController {

    private final ElvaItemService elvaItemService;

    @GetMapping("/top-1000")
    public List<ElvaItemResponse> getTop1000ElvaItems(
            @RequestParam @NotBlank String clientId) { // clientId ajouté
        return elvaItemService.getTop1000ElvaItems(clientId);
    }

    @GetMapping
    public Page<ElvaItemResponse> searchElvaItems(
            @Valid @ModelAttribute ElvaItemSearchRequest searchRequest, // @Valid ajouté pour clientId
            Pageable pageable) {
        return elvaItemService.searchElvaItems(searchRequest, pageable);
    }

    @GetMapping("/equivalences")
    public List<ElvaItemResponse> getEquivalences(
            @RequestParam @NotBlank String no,
            @RequestParam @NotBlank String clientId, // clientId ajouté
            @RequestParam(required = false) String referenceOrigineLie) {
        return elvaItemService.getEquivalences(no, clientId, referenceOrigineLie);
    }

    @GetMapping("/kits")
    public List<ElvaItemResponse> getKitItems(
            @RequestParam @NotBlank String no,
            @RequestParam @NotBlank String clientId) { // clientId ajouté
        return elvaItemService.getKitItems(no, clientId);
    }

    // RBAC Lot 4 : déclenchement de synchronisation = action lourde → ADAPTABLE_SYNC_RUN.
    // (Les endpoints de lecture ci-dessus restent transversaux — voir note Lot 4 : non bloqués ce round.)
    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('ADAPTABLE_SYNC_RUN')")
    public String syncElvaItems() {
        elvaItemService.syncElvaItemsFromSqlServer();
        return "Sync completed successfully";
    }
}
