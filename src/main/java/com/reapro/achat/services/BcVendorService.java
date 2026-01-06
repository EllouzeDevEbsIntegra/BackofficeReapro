package com.reapro.achat.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.DTO.bc.BcVendorBC;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.repositories.primary.AdminRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BcVendorService {

    private final BusinessCentralService bcService;
    private final AdminRepository adminRepository;

    public List<BcVendorBC> getVendors(String userEmail) {
        // Récupération du companyId de l'admin connecté
        Admin admin = adminRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        if (admin.getBcCompanyId() == null || admin.getBcCompanyId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune société BC associée à votre profil.");
        }

        String companyId = admin.getBcCompanyId();

        // Appel à l'API Custom SiVendorAPI
        BcVendorListResponse resp = bcService.getCustom(
                "SiVendorAPI",
                companyId,
                null,
                BcVendorListResponse.class
        );

        return resp != null && resp.getValue() != null ? resp.getValue() : Collections.emptyList();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BcVendorListResponse extends BcListResponse<BcVendorBC> {}
}