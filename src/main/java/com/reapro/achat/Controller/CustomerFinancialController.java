package com.reapro.achat.Controller;

import com.reapro.achat.DTO.CustomerFinancialDetailResponse;
import com.reapro.achat.services.CustomerFinancialService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// RBAC Lot 4 : détails financiers client (dialog Info Client B2B) → B2B_ACCESS (super-admin bypass).
@RestController
@RequestMapping("/api/customer-financials")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('B2B_ACCESS')")
public class CustomerFinancialController {

    private final CustomerFinancialService customerFinancialService;

    @GetMapping("/{codeClient}")
    public CustomerFinancialDetailResponse getCustomerFinancialDetails(@PathVariable String codeClient) {
        return customerFinancialService.getCustomerFinancialDetails(codeClient);
    }
}
