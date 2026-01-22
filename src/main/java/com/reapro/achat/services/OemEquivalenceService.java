package com.reapro.achat.services;

import com.reapro.achat.DTO.OemCountDetailDTO;
import com.reapro.achat.DTO.OemCountDetailsResponseDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OemEquivalenceService {

    private final JdbcTemplate sqlServerJdbcTemplate;
    private final RestTemplate restTemplate;
    private final String oemApiUrl;

    public OemEquivalenceService(
            @Qualifier("sqlServerSimpleJdbcTemplate") JdbcTemplate sqlServerJdbcTemplate,
            RestTemplate restTemplate,
            @Value("${oem.api.url}") String oemApiUrl) {
        this.sqlServerJdbcTemplate = sqlServerJdbcTemplate;
        this.restTemplate = restTemplate;
        this.oemApiUrl = oemApiUrl;
    }

    public OemCountDetailsResponseDTO calculateOemCountWithDetails(String masterItemNo) {
        List<String> equivalentOemReferences = getEquivalentOemReferences(masterItemNo);

        List<OemCountDetailDTO> details = equivalentOemReferences.stream()
                .map(ref -> new OemCountDetailDTO(ref, getOemCountFromApi(ref)))
                .collect(Collectors.toList());

        int totalCount = details.stream()
                .mapToInt(OemCountDetailDTO::getCount)
                .sum();

        return new OemCountDetailsResponseDTO(totalCount, details);
    }

    private List<String> getEquivalentOemReferences(String masterItemNo) {
        String sql = "SELECT DISTINCT (REPLACE([No_],'MASTER','')) FROM [Amiral_LS].[dbo].[ELVA_Item] WHERE [Reference Origine Lié] = ? AND isOEM = 1";
        
        return sqlServerJdbcTemplate.queryForList(sql, String.class, masterItemNo)
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toList());
    }

    private int getOemCountFromApi(String oemReference) {
        String url = UriComponentsBuilder.fromUriString(oemApiUrl)
                .queryParam("oem", oemReference)
                .toUriString();

        try {
            Integer count = restTemplate.getForObject(url, Integer.class);
            return (count != null) ? count : 0;
        } catch (Exception e) {
            log.error("Error calling external OEM API for reference '{}' at URL {}: {}", oemReference, url, e.getMessage());
            return 0;
        }
    }
}
