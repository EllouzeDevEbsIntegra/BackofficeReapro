package com.reapro.achat.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessCentralService {

    private final WebClient.Builder webClientBuilder;
    private final ParameterService parameterService;
    private final ObjectMapper objectMapper;

    // ─────────────────────────────────────────────────────────────
    // Configuration du WebClient
    // ─────────────────────────────────────────────────────────────
    private WebClient getClient() {
        int timeout = parameterService.getIntValue(ParameterService.BC_TIMEOUT, 30000);

        String authHeader = parameterService.getBasicAuthHeader();

        return webClientBuilder
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // URLs de base dynamiques
    // ─────────────────────────────────────────────────────────────
    private String standardBase() {
        return parameterService.getStandardBaseUrl();
    }

    private String customBase() {
        return parameterService.getCustomBaseUrl();
    }

    // ─────────────────────────────────────────────────────────────
    // MÉTHODES PUBLIQUES
    // ─────────────────────────────────────────────────────────────

    /**
     * GET sur une API CUSTOM (extension)
     */
    public <T> T getCustom(String endpoint,
                           String companyId,
                           Map<String, String> queryParams,
                           Class<T> responseType) {
        String url = buildUrl(customBase(), companyId, endpoint, queryParams);
        log.info("BC GET Custom URL = {}", url);
        return execute(getClient().get().uri(url), responseType, "GET Custom");
    }

    /**
     * GET sur une API STANDARD (Microsoft)
     */
    public <T> T getStandard(String endpoint,
                             String companyId,
                             Map<String, String> queryParams,
                             Class<T> responseType) {
        String url = buildUrl(standardBase(), companyId, endpoint, queryParams);
        log.info("BC GET Standard URL = {}", url);
        return execute(getClient().get().uri(url), responseType, "GET Standard");
    }

    /**
     * POST sur API CUSTOM
     */
    public <T, R> R postCustom(String endpoint,
                               String companyId,
                               T body,
                               Class<R> responseType) {
        String url = buildUrl(customBase(), companyId, endpoint, null);
        log.info("BC POST Custom URL = {}", url);
        try {
            log.info("BC POST Body: {}", objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            log.warn("Could not serialize POST body for logging", e);
        }
        return execute(getClient().post().uri(url).bodyValue(body), responseType, "POST Custom");
    }

    /**
     * POST sur API STANDARD (Microsoft)
     */
    public <T, R> R postStandard(String endpoint,
                               String companyId,
                               T body,
                               Class<R> responseType) {
        String url = buildUrl(standardBase(), companyId, endpoint, null);
        log.info("BC POST Standard URL = {}", url);
        try {
            log.info("BC POST Body: {}", objectMapper.writeValueAsString(body));
        } catch (Exception e) {
            log.warn("Could not serialize POST body for logging", e);
        }
        return execute(getClient().post().uri(url).bodyValue(body), responseType, "POST Standard");
    }

    /**
     * PATCH sur une API CUSTOM (extension)
     */
    public <T> T patchCustom(String endpoint,
                             String companyId,
                             String resourceId,
                             Object body,
                             String ifMatch,
                             Class<T> responseType) {

        String path = endpoint + "(" + resourceId + ")";
        String url = buildUrl(customBase(), companyId, path, null);
        log.info("BC PATCH Custom URL = {}", url);

        if (body != null) {
            try {
                log.info("BC PATCH Body: {}", objectMapper.writeValueAsString(body));
            } catch (Exception e) {
                log.warn("Could not serialize PATCH body for logging", e);
            }
        }

        WebClient.RequestBodySpec bodySpec = getClient()
                .patch()
                .uri(url)
                .header(HttpHeaders.IF_MATCH, ifMatch)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        WebClient.RequestHeadersSpec<?> spec = (body != null)
                ? bodySpec.bodyValue(body)
                : bodySpec;

        return execute(spec, responseType, "PATCH Custom");
    }

    /**
     * GET binaire sur API STANDARD (ex : contenu média d'une photo BC).
     * Renvoie le flux brut (image) ; renvoie null si la ressource est absente (404).
     * Aucun secret n'est journalisé (seule l'URL — sans token — est tracée comme pour les autres appels BC).
     */
    public byte[] getStandardBinary(String endpoint, String companyId) {
        String url = buildUrl(standardBase(), companyId, endpoint, null);
        log.info("BC GET Standard (binaire) URL = {}", url);
        try {
            return getBinaryClient().get()
                    .uri(url)
                    .accept(MediaType.ALL)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofMillis(parameterService.getIntValue(ParameterService.BC_TIMEOUT, 30000)))
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            log.info("BC GET Standard (binaire) : contenu absent (404)");
            return null;
        } catch (WebClientResponseException e) {
            log.error("Erreur BC GET binaire (Status {})", e.getStatusCode());
            throw new ApiException(ErrorCode.BC_API_ERROR, "Erreur communication Business Central", e);
        } catch (Exception e) {
            log.error("Erreur BC GET binaire : {}", e.getMessage());
            throw new ApiException(ErrorCode.BC_API_ERROR, "Erreur communication Business Central", e);
        }
    }

    /**
     * PATCH binaire sur API STANDARD (mise à jour du contenu média : flux image brut).
     * {@code contentType} = type MIME de l'image ; {@code ifMatch} = ETag (ou "*").
     */
    public void patchStandardBinary(String endpoint,
                                    String companyId,
                                    byte[] content,
                                    String contentType,
                                    String ifMatch) {
        String url = buildUrl(standardBase(), companyId, endpoint, null);
        log.info("BC PATCH Standard (binaire) URL = {} ({} octets, type={})",
                url, content != null ? content.length : 0, contentType);
        try {
            getBinaryClient().patch()
                    .uri(url)
                    .header(HttpHeaders.IF_MATCH, ifMatch != null ? ifMatch : "*")
                    .contentType(MediaType.parseMediaType(contentType))
                    .bodyValue(content)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofMillis(parameterService.getIntValue(ParameterService.BC_TIMEOUT, 30000)))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Erreur BC PATCH binaire (Status {})", e.getStatusCode());
            throw new ApiException(ErrorCode.BC_API_ERROR, "Erreur communication Business Central", e);
        } catch (Exception e) {
            log.error("Erreur BC PATCH binaire : {}", e.getMessage());
            throw new ApiException(ErrorCode.BC_API_ERROR, "Erreur communication Business Central", e);
        }
    }

    /**
     * DELETE sur API STANDARD ({@code ifMatch} = ETag ou "*").
     */
    public void deleteStandard(String endpoint, String companyId, String ifMatch) {
        String url = buildUrl(standardBase(), companyId, endpoint, null);
        log.info("BC DELETE Standard URL = {}", url);
        try {
            getClient().delete()
                    .uri(url)
                    .header(HttpHeaders.IF_MATCH, ifMatch != null ? ifMatch : "*")
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofMillis(parameterService.getIntValue(ParameterService.BC_TIMEOUT, 30000)))
                    .block();
        } catch (WebClientResponseException e) {
            log.error("Erreur BC DELETE (Status {})", e.getStatusCode());
            throw new ApiException(ErrorCode.BC_API_ERROR, "Erreur communication Business Central", e);
        } catch (Exception e) {
            log.error("Erreur BC DELETE : {}", e.getMessage());
            throw new ApiException(ErrorCode.BC_API_ERROR, "Erreur communication Business Central", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // UTILITAIRES PRIVÉS
    // ─────────────────────────────────────────────────────────────

    /**
     * Client BC pour les échanges binaires : même auth que {@link #getClient()},
     * mais SANS forcer Content-Type/Accept = JSON (on gère ces en-têtes par requête).
     */
    private WebClient getBinaryClient() {
        String authHeader = parameterService.getBasicAuthHeader();
        return webClientBuilder
                .defaultHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .build();
    }

    private String buildUrl(String base,
                            String companyId,
                            String endpoint,
                            Map<String, String> queryParams) {
        StringBuilder url = new StringBuilder(base); // base se termine par /

        if (companyId != null && !companyId.isBlank()) {
            url.append("companies(").append(companyId).append(")/");
        }

        url.append(endpoint);  // ex: "quoteLines"

        if (queryParams != null && !queryParams.isEmpty()) {
            url.append("?");
            queryParams.forEach((k, v) -> url.append(k).append("=").append(v).append("&"));
            url.setLength(url.length() - 1); // retire le dernier "&"
        }
        return url.toString();
    }

    private <T> T execute(WebClient.RequestHeadersSpec<?> spec,
                          Class<T> responseType,
                          String operation) {
        try {
            return spec.retrieve()
                    .bodyToMono(responseType)
                    .timeout(Duration.ofMillis(parameterService.getIntValue(ParameterService.BC_TIMEOUT, 30000)))
                    .block();
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("Erreur BC {} (Status {}): Body={}", operation, e.getStatusCode(), responseBody);
            throw new ApiException(
                    ErrorCode.BC_API_ERROR,
                    "Erreur communication Business Central : " + responseBody,
                    e
            );
        } catch (Exception e) {
            log.error("Erreur BC {} : {}", operation, e.getMessage());
            throw new ApiException(
                    ErrorCode.BC_API_ERROR,
                    "Erreur communication Business Central : " + e.getMessage(),
                    e
            );
        }
    }

    public boolean testConnection() {
        try {
            getClient().get()
                    .uri(standardBase())
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(5))
                    .block();
            log.info("Connexion BC On-Premise OK");
            return true;
        } catch (Exception e) {
            log.error("Connexion BC échouée : {}", e.getMessage());
            return false;
        }
    }
}
