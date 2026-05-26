package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ElvaItemResponse;
import com.reapro.achat.DTO.ElvaItemSearchRequest;
import com.reapro.achat.services.ElvaItemService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/elva-items")
@RequiredArgsConstructor
@org.springframework.validation.annotation.Validated
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

    @PostMapping("/sync")
    public String syncElvaItems() {
        elvaItemService.syncElvaItemsFromSqlServer();
        return "Sync completed successfully";
    }
}
