package com.reapro.achat.services;

import com.reapro.achat.entities.primary.AppParameter;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import com.reapro.achat.repositories.primary.AppParameterRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ParameterService {

    private final AppParameterRepository parameterRepository;

    // ─────────────────────────────────────────────────────────────
    // Clés de paramètres
    // ─────────────────────────────────────────────────────────────
    public static final String BC_SERVER_URL        = "BC_SERVER_URL";
    public static final String BC_API_STANDARD_PATH = "BC_API_STANDARD_PATH";
    public static final String BC_API_CUSTOM_PATH   = "BC_API_CUSTOM_PATH";
    public static final String BC_AUTH_TOKEN        = "BC_AUTH_TOKEN";   // Token Basic (partie après 'Basic ')
    public static final String BC_TIMEOUT           = "BC_TIMEOUT";
    public static final String TECDOC_API_URL       = "TECDOC_API_URL";
    public static final String TECDOC_API_KEY       = "TECDOC_API_KEY";
    public static final String TECDOC_PROVIDER      = "TECDOC_PROVIDER";
    public static final String TECDOC_COUNTRY       = "TECDOC_COUNTRY";

    // ─────────────────────────────────────────────────────────────
    // Valeurs par défaut (application.properties)
    // ─────────────────────────────────────────────────────────────
    @Value("${bc.default.server-url}")
    private String defaultServerUrl;

    @Value("${bc.default.api-standard-path}")
    private String defaultStandardPath;

    @Value("${bc.default.api-custom-path}")
    private String defaultCustomPath;

    @Value("${bc.default.auth-token}")
    private String defaultAuthToken;   // ICI : même format que Postman → partie après "Basic "

    @Value("${bc.default.timeout:30000}")
    private String defaultTimeout;


    // ─────────────────────────────────────────────────────────────
    // Valeurs par défaut (application.properties) - TECDOC
    // ─────────────────────────────────────────────────────────────
    @Value("${td.default.server-url}")
    private String defaultTecDocUrl;

    @Value("${td.default.api-key}")
    private String defaultTecDocApiKey;

    @Value("${td.default.provider}")
    private String defaultTecDocProvider;

    @Value("${td.default.country}")
    private String defaultTecDocCountry;

    // ─────────────────────────────────────────────────────────────
    // Initialisation des paramètres au démarrage
    // ─────────────────────────────────────────────────────────────
    @PostConstruct
    public void initDefaultParameters() {
        createIfNotExists(BC_SERVER_URL,        defaultServerUrl,   "URL Serveur BC (avec IP + port)", "BC", false);
        createIfNotExists(BC_API_STANDARD_PATH, defaultStandardPath,"Chemin API Standard BC",          "BC", false);
        createIfNotExists(BC_API_CUSTOM_PATH,   defaultCustomPath,  "Chemin API Custom (extension)",   "BC", false);

        // ⚠ IMPORTANT : on NE chiffre PAS le token Basic (encrypted = false)
        // La valeur en base est déjà un token Base64 tel qu'utilisé par Postman (partie après 'Basic ')
        createIfNotExists(BC_AUTH_TOKEN,        defaultAuthToken,   "Token Basic (après 'Basic ')",    "BC", false);

        createIfNotExists(BC_TIMEOUT,           defaultTimeout,     "Timeout appels BC (ms)",          "BC", false);

        createIfNotExists(TECDOC_API_URL,  defaultTecDocUrl,      "URL API TecDoc",        "TECDOC", false);
        // On crypte la clé API par sécurité (encrypted = true)
        createIfNotExists(TECDOC_API_KEY,  defaultTecDocApiKey,   "Clé API TecDoc",        "TECDOC", true);
        createIfNotExists(TECDOC_PROVIDER, defaultTecDocProvider, "Provider ID TecDoc",    "TECDOC", false);
        createIfNotExists(TECDOC_COUNTRY,  defaultTecDocCountry,  "Code Pays (ex: TN, FR)","TECDOC", false);

    }

    private void createIfNotExists(String key, String val, String desc, String cat, boolean enc) {
        if (!parameterRepository.existsByParamKey(key)) {
            parameterRepository.save(
                    AppParameter.builder()
                            .paramKey(key)
                            .paramValue(enc ? encode(val) : val)
                            .description(desc)
                            .category(cat)
                            .encrypted(enc)
                            .active(true)
                            .build()
            );
            log.info("Paramètre créé : {} = {}", key, val);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Lecture avec cache
    // ─────────────────────────────────────────────────────────────
    @Cacheable(value = "bcParameters", key = "#key")
    public String getValue(String key) {
        AppParameter param = parameterRepository.findByParamKeyAndActiveTrue(key)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.PARAMETER_NOT_FOUND,
                        "Paramètre manquant : " + key
                ));

        return param.isEncrypted()
                ? decode(param.getParamValue())
                : param.getParamValue();
    }

    public int getIntValue(String key, int defaultValue) {
        try {
            return Integer.parseInt(getValue(key));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // URLs de base (avec / garanti à la fin)
    // ─────────────────────────────────────────────────────────────
    public String getStandardBaseUrl() {
        return ensureSlash(getValue(BC_SERVER_URL)) + ensureSlash(getValue(BC_API_STANDARD_PATH));
    }

    public String getCustomBaseUrl() {
        return ensureSlash(getValue(BC_SERVER_URL)) + ensureSlash(getValue(BC_API_CUSTOM_PATH));
    }

    private String ensureSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    // ─────────────────────────────────────────────────────────────
    // HEADER D'AUTHENTIFICATION BASIC
    // ─────────────────────────────────────────────────────────────
    /**
     * Retourne le header complet "Basic xxxxx..."
     * La valeur en base (BC_AUTH_TOKEN) est déjà un token Base64
     * (partie après "Basic " que tu utilises dans Postman).
     */
    public String getBasicAuthHeader() {
        String token = getValue(BC_AUTH_TOKEN);
        String header = "Basic " + token;
        return header;
    }

    // ─────────────────────────────────────────────────────────────
    // Utils encode/decode (pour d'autres paramètres si un jour chiffrés)
    // ─────────────────────────────────────────────────────────────
    private String encode(String v) {
        return Base64.getEncoder().encodeToString(v.getBytes());
    }

    private String decode(String v) {
        return new String(Base64.getDecoder().decode(v));
    }
}