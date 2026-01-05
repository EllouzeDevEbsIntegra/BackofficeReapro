package com.reapro.achat.Controller;

import com.reapro.achat.DTO.bc.BcCompanyBC;
import com.reapro.achat.services.BcCompanyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bc/companies")
@RequiredArgsConstructor
public class BcCompanyController {

    private final BcCompanyService service;

    @GetMapping
    public List<BcCompanyBC> getCompanies() {
        return service.getCompanies();
    }
}