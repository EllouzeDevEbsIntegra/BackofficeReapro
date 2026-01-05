package com.reapro.achat.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reapro.achat.DTO.tecdoc.*;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TecDocService {

    private final WebClient.Builder webClientBuilder;
    private final ParameterService parameterService;
    private final ObjectMapper objectMapper;

    public TecDocApiResponse searchArticles(String searchQuery, Integer supplierId) {
        TecDocApiResponse resp = searchInternal(searchQuery, 0, true, supplierId);
        if (resp != null && resp.getArticles() != null) {
            for (TecDocArticle article : resp.getArticles()) {
                if (supplierId != null) article.setSupplierLogoUrl(fetchSupplierLogoUrl(supplierId));
                if (article.getGenericArticles() != null && !article.getGenericArticles().isEmpty()) {
                    Long legacyId = article.getGenericArticles().get(0).getLegacyArticleId();
                    if (legacyId != null) article.setLinkedVehicles(fetchLinkedVehicles(legacyId));
                }
            }
        }
        return resp;
    }

    private List<TecDocArticle.VehicleManufacturer> fetchLinkedVehicles(Long legacyArticleId) {
        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        String country = parameterService.getValue(ParameterService.TECDOC_COUNTRY);
        Long providerId = Long.parseLong(parameterService.getValue(ParameterService.TECDOC_PROVIDER));

        Map<String, Object> requestBody = Map.of("getArticleLinkedAllLinkingTargetManufacturer2", Map.of(
                "articleCountry", country, "articleId", legacyArticleId, "country", country,
                "lang", "FR", "linkingTargetType", "P", "provider", providerId));

        try {
            Map<?, ?> response = webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                    .bodyValue(requestBody).retrieve().bodyToMono(Map.class).block();

            if (response != null && response.get("data") instanceof Map<?, ?> data) {
                if (data.get("array") instanceof List<?> array) {
                    return array.stream().map(m -> {
                        Map<?, ?> map = (Map<?, ?>) m;
                        TecDocArticle.VehicleManufacturer vm = new TecDocArticle.VehicleManufacturer();
                        vm.setManuId(Long.valueOf(map.get("manuId").toString()));
                        vm.setManuName(String.valueOf(map.get("manuName")));
                        return vm;
                    }).collect(Collectors.toList());
                }
            }
        } catch (Exception e) { log.warn("Erreur fetchLinkedVehicles: {}", e.getMessage()); }
        return Collections.emptyList();
    }

    public List<TecDocVehicleDetail> getLinkedVehiclesDetails(Long articleId, Long manuId) {
        log.info("[TECDOC] Début Step 1 pour articleId: {} et manuId: {}", articleId, manuId);

        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        String country = parameterService.getValue(ParameterService.TECDOC_COUNTRY);
        Long providerId = Long.parseLong(parameterService.getValue(ParameterService.TECDOC_PROVIDER));

        Map<String, Object> step1Request = Map.of("getArticleLinkedAllLinkingTarget4", Map.of(
                "articleCountry", country, "articleId", articleId, "country", country,
                "lang", "FR", "linkingTargetManuId", manuId, "linkingTargetType", "P", "provider", providerId));

        try {
            Map<?, ?> resp1 = webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                    .bodyValue(step1Request).retrieve().bodyToMono(Map.class).block();

            List<Map<String, Object>> linkages = extractLinkages(resp1);
            if (linkages.isEmpty()) return Collections.emptyList();

            log.info("[TECDOC] Step 2: {} liens trouvés. Découpage par lots.", linkages.size());

            List<TecDocVehicleDetail> allResults = new ArrayList<>();

            // On traite par lots de 10 pour éviter de saturer l'API
            for (int i = 0; i < linkages.size(); i += 10) {
                int end = Math.min(i + 10, linkages.size());
                List<Map<String, Object>> batch = linkages.subList(i, end);

                List<Map<String, Object>> pairs = batch.stream()
                        .map(l -> Map.of("articleLinkId", l.get("articleLinkId"), "linkingTargetId", l.get("linkingTargetId")))
                        .collect(Collectors.toList());

                Map<String, Object> step2Request = Map.of("getArticleLinkedAllLinkingTargetsByIds3", Map.of(
                        "articleCountry", country, "articleId", articleId, "lang", "FR",
                        "linkedArticlePairs", Map.of("array", pairs), "linkingTargetType", "P", "provider", providerId));

                Map<?, ?> resp2 = webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                        .bodyValue(step2Request).retrieve().bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(15)).block();

                allResults.addAll(parseVehicleDetails(resp2));
            }

            log.info("[TECDOC] Total: {} véhicules récupérés.", allResults.size());
            return allResults;

        } catch (Exception e) {
            log.error("[TECDOC] Erreur : {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> extractLinkages(Map<?, ?> response) {
        try {
            if (response.get("data") instanceof Map<?, ?> data) {
                if (data.get("array") instanceof List<?> array && !array.isEmpty()) {
                    if (((Map<?, ?>) array.get(0)).get("articleLinkages") instanceof Map<?, ?> artLinks) {
                        return (List<Map<String, Object>>) artLinks.get("array");
                    }
                }
            }
        } catch (Exception e) { log.warn("Erreur extraction linkages"); }
        return Collections.emptyList();
    }

    private List<TecDocVehicleDetail> parseVehicleDetails(Map<?, ?> response) {
        List<TecDocVehicleDetail> results = new ArrayList<>();
        try {
            List<?> mainArray = null;
            if (response.get("data") instanceof Map<?, ?> data) {
                mainArray = (List<?>) data.get("array");
            } else if (response.get("array") instanceof List<?> array) {
                mainArray = array;
            }

            if (mainArray == null) return results;

            for (Object obj : mainArray) {
                Map<?, ?> item = (Map<?, ?>) obj;
                if (item.get("linkedVehicles") instanceof Map<?, ?> linkedVehicles) {
                    if (linkedVehicles.get("array") instanceof List<?> vArray) {
                        for (Object vObj : vArray) {
                            Map<?, ?> v = (Map<?, ?>) vObj;
                            TecDocVehicleDetail d = new TecDocVehicleDetail();
                            d.setCarId(v.get("carId") != null ? Long.valueOf(v.get("carId").toString()) : null);
                            d.setCarDesc(String.valueOf(v.get("carDesc")));
                            d.setManuDesc(String.valueOf(v.get("manuDesc")));
                            d.setModelDesc(String.valueOf(v.get("modelDesc")));
                            d.setConstructionType(String.valueOf(v.get("constructionType")));
                            d.setCylinderCapacity(safeInt(v.get("cylinderCapacity")));
                            d.setPowerHpFrom(safeInt(v.get("powerHpFrom")));
                            d.setPowerKwFrom(safeInt(v.get("powerKwFrom")));
                            d.setYearOfConstructionFrom(safeInt(v.get("yearOfConstructionFrom")));
                            d.setYearOfConstructionTo(safeInt(v.get("yearOfConstructionTo")));
                            results.add(d);
                        }
                    }
                }
            }
        } catch (Exception e) { log.error("Erreur parsing: {}", e.getMessage()); }
        return results;
    }

    private Integer safeInt(Object obj) {
        if (obj == null) return null;
        try {
            return (obj instanceof Number n) ? n.intValue() : Double.valueOf(obj.toString()).intValue();
        } catch (Exception e) { return null; }
    }

    private String fetchSupplierLogoUrl(Integer supplierId) {
        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        Map<String, Object> body = Map.of("getBrands", Map.of(
                "articleCountry", parameterService.getValue(ParameterService.TECDOC_COUNTRY),
                "provider", Long.parseLong(parameterService.getValue(ParameterService.TECDOC_PROVIDER)),
                "lang", "FR", "dataSupplierIds", supplierId, "includeDataSupplierLogo", true));
        try {
            TecDocBrandsResponse resp = webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                    .bodyValue(body).retrieve().bodyToMono(TecDocBrandsResponse.class).block();
            return resp.getData().getArray().get(0).getDataSupplierLogo().getImageURL200();
        } catch (Exception e) { return null; }
    }

    public TecDocApiResponse searchArticlesType1(String searchQuery) { return searchInternal(searchQuery, 1, false, null); }

    private TecDocApiResponse searchInternal(String searchQuery, int searchType, boolean includeAll, Integer supplierId) {
        String url = parameterService.getValue(ParameterService.TECDOC_API_URL);
        String apiKey = parameterService.getValue(ParameterService.TECDOC_API_KEY);
        String country = parameterService.getValue(ParameterService.TECDOC_COUNTRY);
        Long providerId = Long.parseLong(parameterService.getValue(ParameterService.TECDOC_PROVIDER));

        Map<String, Object> params = new HashMap<>(Map.of(
                "articleCountry", country, "provider", providerId, "searchQuery", searchQuery,
                "searchType", searchType, "lang", "FR", "includeAll", includeAll));
        if (supplierId != null && supplierId > 0) params.put("dataSupplierIds", supplierId);

        try {
            return webClientBuilder.build().post().uri(url).header("api-key", apiKey)
                    .bodyValue(Map.of("getArticles", params)).retrieve().bodyToMono(TecDocApiResponse.class).block();
        } catch (Exception e) { throw new ApiException(ErrorCode.BC_API_ERROR, "Erreur TecDoc"); }
    }
}