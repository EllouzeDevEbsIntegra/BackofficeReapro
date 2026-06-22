package com.reapro.achat.Controller;

import com.reapro.achat.DTO.BcItemEnrichedResponse;
import com.reapro.achat.DTO.PagedResponse;
import com.reapro.achat.DTO.bc.BcItemBC;
import com.reapro.achat.DTO.bc.SiItemCategory;
import com.reapro.achat.DTO.bc.BcManufacturer;
import com.reapro.achat.services.BcManufacturerService;
import com.reapro.achat.services.BcItemBCService;
import com.reapro.achat.services.CompanyScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

// RBAC Lot 4bis-A : société strictement celle de l'utilisateur authentifié.
// Le paramètre client `companyId` (anciennement @RequestParam, GUID par défaut) est SUPPRIMÉ → plus de
// company-spoofing. La société est résolue depuis le profil (CompanyScopeService) ; absente → 403.
// RBAC Lot 4bis-B : équivalences / catégories / fabricants (Info Article) → lecture si Info Article OU
// module consommateur. NB : PATCH itemsEqv/{no}/toVerify est une écriture BC encore couverte par cette
// permission de lecture ; à durcir via ARTICLE_WRITE/BC_WRITE dans un lot ultérieur (voir « risques »).
@RestController
@RequestMapping("/api/bc")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ARTICLE_INFO_READ','COMPARATOR_ACCESS','PURCHASE_CONFIRMATION_ACCESS','B2B_ACCESS','ARTICLE_MANAGEMENT_ACCESS','TECDOC_CATALOG_ACCESS')")
public class BcItemBCController {

    private final BcItemBCService service;
    private final BcManufacturerService manufacturerService;
    private final CompanyScopeService companyScopeService;

    @GetMapping("/itemsEqv")
    public PagedResponse<BcItemEnrichedResponse> getItems(
            @AuthenticationPrincipal String email,
            @RequestParam String referenceMaster,
            @RequestParam String no,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String compareQuoteNo,
            @RequestParam(required = false) String stockOperator,
            @RequestParam(required = false) BigDecimal stockValue,
            @RequestParam(required = false) String dateDernierAchatOperator,
            @RequestParam(required = false) String dateDernierAchatValue,
            @RequestParam(required = false) String referenceOperator,
            @RequestParam(required = false) String referenceValue
    ) {
        String companyId = companyScopeService.requireUserCompanyId(email);
        return service.getItemsByReferenceAndNotNoSortedLocally(companyId, referenceMaster, no, page, size, compareQuoteNo,
                stockOperator, stockValue, dateDernierAchatOperator, dateDernierAchatValue,
                referenceOperator, referenceValue);
    }

    @PatchMapping("/itemsEqv/{no}/toVerify")
    public BcItemBC updateToVerify(
            @AuthenticationPrincipal String email,
            @PathVariable String no
    ) {
        String companyId = companyScopeService.requireUserCompanyId(email);
        return service.updateToVerifyByNo(companyId, no);
    }

    @GetMapping("/categories")
    public List<SiItemCategory> getItemCategories(
            @AuthenticationPrincipal String email,
            @RequestParam(required = false) Integer indentation,
            @RequestParam(required = false) String parentCategory
    ) {
        String companyId = companyScopeService.requireUserCompanyId(email);
        return service.getItemCategories(companyId, indentation, parentCategory);
    }

    @GetMapping("/manufacturers")
    public List<BcManufacturer> getManufacturers(
            @AuthenticationPrincipal String email
    ) {
        String companyId = companyScopeService.requireUserCompanyId(email);
        return manufacturerService.getManufacturers(companyId);
    }
}
