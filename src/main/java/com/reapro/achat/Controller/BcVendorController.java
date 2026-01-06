package com.reapro.achat.Controller;

import com.reapro.achat.DTO.bc.BcVendorBC;
import com.reapro.achat.services.BcVendorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/bc/vendors")
@RequiredArgsConstructor
public class BcVendorController {

    private final BcVendorService service;

    /**
     * GET /api/bc/vendors
     * Retourne la liste des fournisseurs de la société BC de l'utilisateur
     */
    @GetMapping
    public List<BcVendorBC> getVendors(@AuthenticationPrincipal String email) {
        return service.getVendors(email);
    }
}