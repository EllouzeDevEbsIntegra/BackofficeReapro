package com.reapro.achat.Controller;

import com.reapro.achat.DTO.ElvaItemResponse;
import com.reapro.achat.DTO.ElvaItemSearchRequest;
import com.reapro.achat.services.ElvaItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/elva-items")
@RequiredArgsConstructor
public class ElvaItemController {

    private final ElvaItemService elvaItemService;

    @GetMapping("/top-1000")
    public List<ElvaItemResponse> getTop1000ElvaItems() {
        return elvaItemService.getTop1000ElvaItems();
    }

    @GetMapping
    public Page<ElvaItemResponse> searchElvaItems(
            @ModelAttribute ElvaItemSearchRequest searchRequest,
            Pageable pageable) {
        return elvaItemService.searchElvaItems(searchRequest, pageable);
    }

    @GetMapping("/equivalences")
    public List<ElvaItemResponse> getEquivalences(
            @org.springframework.web.bind.annotation.RequestParam String no,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String referenceOrigineLie) {
        return elvaItemService.getEquivalences(no, referenceOrigineLie);
    }

    @PostMapping("/sync")
    public String syncElvaItems() {
        elvaItemService.syncElvaItemsFromSqlServer();
        return "Sync completed successfully";
    }
}
