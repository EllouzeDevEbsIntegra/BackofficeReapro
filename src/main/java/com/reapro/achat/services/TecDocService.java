package com.reapro.achat.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reapro.achat.DTO.tecdoc.TecDocApiResponse;
import com.reapro.achat.DTO.tecdoc.TecDocArticle;
import com.reapro.achat.DTO.tecdoc.TecDocBrandsRequest;
import com.reapro.achat.DTO.tecdoc.TecDocBrandsResponse;
import com.reapro.achat.DTO.tecdoc.TecDocBrand;
import com.reapro.achat.DTO.tecdoc.TecDocSearchRequest;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class TecDocService {

    private final WebClient.Builder webClientBuilder;
    private final ParameterService parameterService;
    private final ObjectMapper objectMapper;

    /**
     * Recherche Standard (Type 0)
     * Utilisée par /api/tecdoc/articles
     */
    public TecDocApiResponse searchArticles(String searchQuery, Integer supplierId) {
        TecDocApiResponse resp = searchInternal(searchQuery, 0, true, supplierId);

        // ✅ Enrichir les articles avec le logo du fournisseur (si supplierId fourni)
        if (supplierId != null && resp != null && resp.getArticles() != null && !resp.getArticles().isEmpty()) {
            String logoUrl = fetchSupplierLogoUrl(supplierId);
            if (logoUrl != null) {
                for (TecDocArticle a : resp.getArticles()) {
                    a.setSupplierLogoUrl(logoUrl);
                }
            }
        }

        return resp;
    }

    /**
     * Recherche Stricte pour Vérification (Type 1)
     * Utilisée par ArticleVerificationService.
     */
    public TecDocApiResponse searchArticlesType1(String searchQuery) {
        return searchInternal(searchQuery, 1, false, null);
    }

    /**
     * Méthode interne générique pour getArticles
     */
    private TecDocApiResponse searchInternal(String searchQuery, int searchType, boolean includeAll, Integer supplierId) {

        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        String country = parameterService.getValue(ParameterService.TECDOC_COUNTRY);

        Long providerId;
        try {
            String p = parameterService.getValue(ParameterService.TECDOC_PROVIDER);
            providerId = Long.parseLong(p);
        } catch (NumberFormatException e) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "Provider ID TecDoc invalide (doit être un nombre) : "
                            + parameterService.getValue(ParameterService.TECDOC_PROVIDER));
        }

        TecDocSearchRequest.GetArticlesParams params = TecDocSearchRequest.GetArticlesParams.builder()
                .articleCountry(country)
                .provider(providerId)
                .searchQuery(searchQuery)
                .searchType(searchType)
                .lang("FR")
                .includeAll(includeAll)
                .build();

        if (supplierId != null && supplierId > 0) {
            params.setDataSupplierIds(supplierId);
        }

        TecDocSearchRequest requestBody = TecDocSearchRequest.builder()
                .getArticles(params)
                .build();

        // DEBUG JSON
        try {
            if (log.isDebugEnabled()) {
                String jsonLog = objectMapper.writeValueAsString(requestBody);
                log.debug("TECDOC REQUEST JSON (Type {}): {}", searchType, jsonLog);
            }
        } catch (Exception e) {
            log.warn("Impossible de logger le JSON TecDoc", e);
        }

        try {
            return webClientBuilder.build()
                    .post()
                    .uri(url)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(TecDocApiResponse.class)
                    .timeout(Duration.ofSeconds(20))
                    .block();

        } catch (Exception e) {
            log.error("Erreur Appel TecDoc (Type {}): {}", searchType, e.getMessage());
            throw new ApiException(ErrorCode.BC_API_ERROR, "Erreur TecDoc: " + e.getMessage());
        }
    }

    /**
     * Appelle TecDoc getBrands pour récupérer le logo du fournisseur.
     * On renvoie l'URL imageURL200 si dispo, sinon une autre taille, sinon null.
     */
    private String fetchSupplierLogoUrl(Integer supplierId) {
        if (supplierId == null || supplierId <= 0) {
            return null;
        }

        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        String country = parameterService.getValue(ParameterService.TECDOC_COUNTRY);

        Long providerId;
        try {
            String p = parameterService.getValue(ParameterService.TECDOC_PROVIDER);
            providerId = Long.parseLong(p);
        } catch (NumberFormatException e) {
            log.error("Provider ID TecDoc invalide pour getBrands : {}", e.getMessage());
            return null;
        }

        TecDocBrandsRequest.GetBrandsParams params = TecDocBrandsRequest.GetBrandsParams.builder()
                .articleCountry(country)
                .provider(providerId)
                .lang("FR")
                .dataSupplierIds(supplierId)
                .includeDataSupplierLogo(true)
                .build();

        TecDocBrandsRequest body = TecDocBrandsRequest.builder()
                .getBrands(params)
                .build();

        try {
            TecDocBrandsResponse resp = webClientBuilder.build()
                    .post()
                    .uri(url)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(TecDocBrandsResponse.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            if (resp == null
                    || resp.getData() == null
                    || resp.getData().getArray() == null
                    || resp.getData().getArray().isEmpty()) {
                return null;
            }

            // On prend la première entrée correspondant à notre supplierId
            return resp.getData().getArray().stream()
                    .filter(b -> Objects.equals(b.getDataSupplierId(), Long.valueOf(supplierId)))
                    .map(TecDocBrand::getDataSupplierLogo)
                    .filter(Objects::nonNull)
                    .map(logo -> {
                        if (logo.getImageURL200() != null) return logo.getImageURL200();
                        if (logo.getImageURL400() != null) return logo.getImageURL400();
                        if (logo.getImageURL100() != null) return logo.getImageURL100();
                        return logo.getImageURL800();
                    })
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);

        } catch (Exception e) {
            log.error("Erreur TecDoc getBrands (logo fournisseur) : {}", e.getMessage());
            return null;
        }
    }
}