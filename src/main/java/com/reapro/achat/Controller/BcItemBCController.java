package com.reapro.achat.Controller;

import com.reapro.achat.DTO.BcItemEnrichedResponse;
import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.DTO.bc.BcItemBC;
import com.reapro.achat.DTO.bc.SiItemCategory;
import com.reapro.achat.DTO.bc.BcManufacturer;
import com.reapro.achat.services.BcManufacturerService;
import com.reapro.achat.services.BcItemBCService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bc")
@RequiredArgsConstructor
public class BcItemBCController {

    private final BcItemBCService service;
    private final BcManufacturerService manufacturerService;

    @GetMapping("/itemsEqv")
    public PagedResponse<BcItemEnrichedResponse> getItems(
            @RequestParam String referenceMaster,
            @RequestParam String no,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId,
            @RequestParam(required = false) String compareQuoteNo
    ) {
        return service.getItemsByReferenceAndNotNoSortedLocally(companyId, referenceMaster, no, page, size, compareQuoteNo);
    }

    @PatchMapping("/itemsEqv/{no}/toVerify")
    public BcItemBC updateToVerify(
            @PathVariable String no,
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId
    ) {
        return service.updateToVerifyByNo(companyId, no);
    }

    @GetMapping("/categories")
    public List<SiItemCategory> getItemCategories(
            @RequestParam(required = false) Integer indentation,
            @RequestParam(required = false) String parentCategory,
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId
    ) {
        return service.getItemCategories(companyId, indentation, parentCategory);
    }

    @GetMapping("/manufacturers")
    public List<BcManufacturer> getManufacturers(
            @RequestParam(defaultValue = "20C5337E-2E49-EC11-A103-00155DB6A301") String companyId
    ) {
        return manufacturerService.getManufacturers(companyId);
    }
}
