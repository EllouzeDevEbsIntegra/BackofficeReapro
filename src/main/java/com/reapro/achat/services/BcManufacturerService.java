package com.reapro.achat.services;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reapro.achat.DTO.bc.BcListResponse;
import com.reapro.achat.DTO.bc.BcManufacturer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BcManufacturerService {

    private final BusinessCentralService bcService;

    // Cache pendant 30min (configuré dans CacheConfig ou par défaut)
    // On suppose que la liste des fabricants ne change pas toutes les 5 min
    @Cacheable(value = "bcManufacturers", key = "#companyId")
    public List<BcManufacturer> getManufacturers(String companyId) {
        log.info("CACHE MISS - Chargement des fabricants BC pour companyId={}", companyId);

        // Pas de filtre spécial, on récupère tout pour filtrer en mémoire Java
        BcManufacturerListResponse resp = bcService.getCustom(
                "Manufacturer",
                companyId,
                null,
                BcManufacturerListResponse.class
        );

        List<BcManufacturer> list = resp != null && resp.getValue() != null ? new ArrayList<>(resp.getValue()) : new ArrayList<>();
        list.sort((a, b) -> {
            String nameA = a.getName() != null ? a.getName() : "";
            String nameB = b.getName() != null ? b.getName() : "";
            return nameA.compareToIgnoreCase(nameB);
        });
        return list;
    }

    @Data
    @EqualsAndHashCode(callSuper = false)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BcManufacturerListResponse extends BcListResponse<BcManufacturer> {}
}
