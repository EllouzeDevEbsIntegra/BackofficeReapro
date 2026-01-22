package com.reapro.achat.services;

import com.reapro.achat.entities.primary.AppParameter;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import com.reapro.achat.repositories.primary.AppParameterRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
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

    // ... (le reste du fichier reste identique)

    // ─────────────────────────────────────────────────────────────
    // Clés de paramètres
    // ─────────────────────────────────────────────────────────────
    public static final String BC_SERVER_URL        = "BC_SERVER_URL";
    public static final String BC_API_STANDARD_PATH = "BC_API_STANDARD_PATH";
    public static final String BC_API_CUSTOM_PATH   = "BC_API_CUSTOM_PATH";
    public static final String BC_AUTH_TOKEN        = "BC_AUTH_TOKEN";
    public static final String BC_TIMEOUT           = "BC_TIMEOUT";
    public static final String TECDOC_API_URL       = "TECDOC_API_URL";
    public static final String TECDOC_API_KEY       = "TECDOC_API_KEY";
    public static final String TECDOC_PROVIDER      = "TECDOC_PROVIDER";
    public static final String TECDOC_COUNTRY       = "TECDOC_COUNTRY";

    // ... (valeurs par défaut)

    @Value("${bc.default.server-url}")
    private String defaultServerUrl;

    @Value("${bc.default.api-standard-path}")
    private String defaultStandardPath;

    @Value("${bc.default.api-custom-path}")
    private String defaultCustomPath;

    @Value("${bc.default.auth-token}")
    private String defaultAuthToken;

    @Value("${bc.default.timeout:30000}")
    private String defaultTimeout;

    @Value("${td.default.server-url}")
    private String defaultTecDocUrl;

    @Value("${td.default.api-key}")
    private String defaultTecDocApiKey;

    @Value("${td.default.provider}")
    private String defaultTecDocProvider;

    @Value("${td.default.country}")
    private String defaultTecDocCountry;

    @PostConstruct
    public void initDefaultParameters() {
        createIfNotExists(BC_SERVER_URL,        defaultServerUrl,   "URL Serveur BC (avec IP + port)", "BC", false);
        createIfNotExists(BC_API_STANDARD_PATH, defaultStandardPath,"Chemin API Standard BC",          "BC", false);
        createIfNotExists(BC_API_CUSTOM_PATH,   defaultCustomPath,  "Chemin API Custom (extension)",   "BC", false);
        createIfNotExists(BC_AUTH_TOKEN,        defaultAuthToken,   "Token Basic (après 'Basic ')",    "BC", false);
        createIfNotExists(BC_TIMEOUT,           defaultTimeout,     "Timeout appels BC (ms)",          "BC", false);
        createIfNotExists(TECDOC_API_URL,  defaultTecDocUrl,      "URL API TecDoc",        "TECDOC", false);
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

    // ✅ NOUVELLE MÉTHODE DE MISE À JOUR
    @CacheEvict(value = "bcParameters", key = "#key") // Vider le cache pour cette clé
    public void updateValue(String key, String newValue) {
        AppParameter param = parameterRepository.findByParamKey(key)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.PARAMETER_NOT_FOUND,
                        "Paramètre introuvable : " + key
                ));

        if (param.isEncrypted()) {
            param.setParamValue(encode(newValue));
        } else {
            param.setParamValue(newValue);
        }
        parameterRepository.save(param);
        log.info("Paramètre mis à jour : {} = {}", key, newValue);
    }

    public String getStandardBaseUrl() {
        return ensureSlash(getValue(BC_SERVER_URL)) + ensureSlash(getValue(BC_API_STANDARD_PATH));
    }

    public String getCustomBaseUrl() {
        return ensureSlash(getValue(BC_SERVER_URL)) + ensureSlash(getValue(BC_API_CUSTOM_PATH));
    }

    private String ensureSlash(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    public String getBasicAuthHeader() {
        String token = getValue(BC_AUTH_TOKEN);
        return "Basic " + token;
    }

    private String encode(String v) {
        return Base64.getEncoder().encodeToString(v.getBytes());
    }

    private String decode(String v) {
        return new String(Base64.getDecoder().decode(v));
    }
}
