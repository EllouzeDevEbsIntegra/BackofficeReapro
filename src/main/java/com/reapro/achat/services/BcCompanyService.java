package com.reapro.achat.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reapro.achat.DTO.bc.BcCompanyBC;
import com.reapro.achat.DTO.bc.BcListResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class BcCompanyService {

    private final BusinessCentralService bcService;

    public List<BcCompanyBC> getCompanies() {

        Map<String, String> params = new LinkedHashMap<>();
        // ✅ on ajoute displayName
        params.put("$select", "id,name,displayName");

        BcCompanyListResponse resp = bcService.getCustom(
                "companies",
                null,      // important: endpoint racine
                params,
                BcCompanyListResponse.class
        );

        return resp != null && resp.getValue() != null
                ? resp.getValue()
                : Collections.emptyList();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BcCompanyListResponse extends BcListResponse<BcCompanyBC> {}
}