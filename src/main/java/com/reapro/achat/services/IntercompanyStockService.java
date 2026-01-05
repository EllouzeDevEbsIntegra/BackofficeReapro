package com.reapro.achat.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reapro.achat.DTO.IntercompanyStockResponse;
import com.reapro.achat.DTO.bc.BcCompanyBC;
import com.reapro.achat.DTO.bc.BcItemStockBC;
import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.repositories.primary.AdminRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class IntercompanyStockService {

    private final AdminRepository adminRepository;
    private final BcCompanyService bcCompanyService;
    private final BusinessCentralService bcService;

    public List<IntercompanyStockResponse> getIntercompanyStockExcludingMyCompany(String userEmail, String itemNo) {

        Admin admin = adminRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        if (admin.getBcCompanyId() == null || admin.getBcCompanyId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Aucune société BC n'est affectée à votre profil. Veuillez choisir une société.");
        }

        String myCompanyId = admin.getBcCompanyId().trim();

        // Charger toutes les sociétés depuis BC et exclure celle du user
        List<BcCompanyBC> otherCompanies = bcCompanyService.getCompanies().stream()
                .filter(c -> c.getId() != null && !c.getId().equalsIgnoreCase(myCompanyId))
                .collect(Collectors.toList());

        // Préparer filtre bcItems
        Map<String, String> params = new LinkedHashMap<>();
        params.put("$filter", "no eq '" + escapeOData(itemNo) + "'");
        // ✅ On ajoute lastPurshDate dans le $select
        params.put("$select", "no,qtyStock,lastPurshDate");

        List<IntercompanyStockResponse> result = new ArrayList<>();

        for (BcCompanyBC c : otherCompanies) {
            BcItemStockListResponse resp = bcService.getCustom(
                    "bcItems",
                    c.getId(),
                    params,
                    BcItemStockListResponse.class
            );

            BigDecimal stock = BigDecimal.ZERO;
            String returnedNo = itemNo;
            LocalDate lastPurshDate = null;

            if (resp != null && resp.getValue() != null && !resp.getValue().isEmpty()) {
                BcItemStockBC item = resp.getValue().get(0);
                returnedNo = item.getNo() != null ? item.getNo() : itemNo;
                stock = item.getQtyStock() != null ? item.getQtyStock() : BigDecimal.ZERO;
                lastPurshDate = item.getLastPurshDate();
            }

            String companyLabel = (c.getDisplayName() != null && !c.getDisplayName().isBlank())
                    ? c.getDisplayName()
                    : c.getName();

            // ✅ On renvoie aussi lastPurchaseDate
            result.add(new IntercompanyStockResponse(
                    c.getId(),
                    companyLabel,
                    returnedNo,
                    stock,
                    lastPurshDate
            ));
        }

        // Optionnel : trier par nom de société
        result.sort(Comparator.comparing(IntercompanyStockResponse::getCompany, String.CASE_INSENSITIVE_ORDER));

        return result;
    }

    private String escapeOData(String s) {
        return s == null ? "" : s.replace("'", "''");
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BcItemStockListResponse extends BcListResponse<BcItemStockBC> {}
}