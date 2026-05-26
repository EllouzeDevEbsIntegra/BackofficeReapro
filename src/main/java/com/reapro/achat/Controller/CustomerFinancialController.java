package com.reapro.achat.Controller;

import com.reapro.achat.DTO.CustomerFinancialDetailResponse;
import com.reapro.achat.services.CustomerFinancialService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-financials")
@RequiredArgsConstructor
public class CustomerFinancialController {

    private final CustomerFinancialService customerFinancialService;

    @GetMapping("/{codeClient}")
    public CustomerFinancialDetailResponse getCustomerFinancialDetails(@PathVariable String codeClient) {
        return customerFinancialService.getCustomerFinancialDetails(codeClient);
    }
}
