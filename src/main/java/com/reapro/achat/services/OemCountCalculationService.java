package com.reapro.achat.services;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Objects;

@Service
public class OemCountCalculationService {

    private final NamedParameterJdbcTemplate sqlServerJdbcTemplate;
    private final WebClient webClient;

    @Value("${oem.api.url}")
    private String oemApiBaseUrl;

    public OemCountCalculationService(
            @Qualifier("sqlServerJdbcTemplate") NamedParameterJdbcTemplate sqlServerJdbcTemplate,
            WebClient.Builder webClientBuilder) {
        this.sqlServerJdbcTemplate = sqlServerJdbcTemplate;
        this.webClient = webClientBuilder.build();
    }

    public Mono<Integer> calculateOemCount(String masterItemNo) {
        // 1. Fetch OEM references from SQL Server
        String sql = "SELECT DISTINCT (REPLACE([No_],'MASTER','')) FROM [Amiral_LS].[dbo].[ELVA_Item] WHERE [Reference Origine Lié] = :masterItemNo AND isOEM = 1";
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("masterItemNo", masterItemNo);

        List<String> oemReferences = sqlServerJdbcTemplate.queryForList(sql, params, String.class);

        // 2. For each OEM reference, call the external API to get its count
        // 3. Sum up all counts
        return Flux.fromIterable(oemReferences)
                .flatMap(oemRef -> getOemCountFromExternalApi(oemRef))
                .filter(Objects::nonNull) // Filter out any null counts if API call fails
                .reduce(0, Integer::sum); // Sum all counts
    }

    private Mono<Integer> getOemCountFromExternalApi(String oem) {
        String url = oemApiBaseUrl + "?oem=" + oem;
        return webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Integer.class)
                .onErrorResume(e -> {
                    // Log the error and return 0 for this specific OEM reference
                    System.err.println("Error calling OEM API for oem=" + oem + ": " + e.getMessage());
                    return Mono.just(0);
                });
    }
}
