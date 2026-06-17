package com.reapro.achat.partslink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implémentation réelle : connecte un {@link RemoteWebDriver} au Selenium Grid puis
 * authentifie la session via {@link PartslinkLoginHelper}.
 *
 * <p>Reprend la logique de disponibilité Grid de l'ancien {@code PartslinkSessionService},
 * mais <b>sans</b> supprimer agressivement toutes les sessions Grid à chaque création
 * (ce qui tuerait les autres slots du pool). Le nettoyage des orphelines n'est fait
 * qu'une seule fois, au tout premier démarrage.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultPartslinkBrowserSessionFactory implements PartslinkBrowserSessionFactory {

    private final PartslinkProperties properties;
    private final PartslinkLoginHelper loginHelper;

    @Override
    public PartslinkBrowserSession create(int slotIndex) {
        loginHelper.validateCredentialsConfigured();
        verifyRemoteSeleniumReady();

        RemoteWebDriver driver;
        try {
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--no-sandbox");
            options.addArguments("--window-size=1920,1080");
            options.addArguments("--lang=fr-FR");
            driver = new RemoteWebDriver(URI.create(properties.getRemoteWebDriverUrl()).toURL(), options);
        } catch (Exception ex) {
            throw new PartslinkSeleniumStartupException(
                    "Impossible de créer la session Chrome (slot " + slotIndex + "): "
                            + loginHelper.sanitize(ex.getMessage()), ex);
        }

        try {
            loginHelper.login(driver);
        } catch (RuntimeException ex) {
            try {
                driver.quit();
            } catch (Exception ignored) {
                // best effort
            }
            throw ex;
        }

        log.info("[Partslink] Slot {} créé et authentifié (sessionId={}).",
                slotIndex, driver.getSessionId());
        return new PartslinkBrowserSession(slotIndex, driver, loginHelper);
    }

    @Override
    public void cleanupOrphansOnce() {
        String gridBase = buildGridBaseUrl(properties.getRemoteWebDriverUrl());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            HttpRequest listReq = HttpRequest.newBuilder(URI.create(gridBase + "/sessions"))
                    .GET().timeout(Duration.ofSeconds(5)).build();
            HttpResponse<String> listResp = httpClient.send(listReq, HttpResponse.BodyHandlers.ofString());
            String body = listResp.body() == null ? "" : listResp.body();
            Matcher matcher = Pattern
                    .compile("\"id\":\\s*\"([a-f0-9]{32,})\"", Pattern.CASE_INSENSITIVE)
                    .matcher(body);
            while (matcher.find()) {
                String orphanId = matcher.group(1);
                log.info("[Partslink] Suppression session Selenium orpheline: {}", orphanId);
                try {
                    HttpRequest delReq = HttpRequest.newBuilder(URI.create(gridBase + "/session/" + orphanId))
                            .DELETE().timeout(Duration.ofSeconds(5)).build();
                    httpClient.send(delReq, HttpResponse.BodyHandlers.discarding());
                } catch (Exception ex) {
                    log.warn("[Partslink] Échec suppression orpheline {}: {}", orphanId, ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("[Partslink] Listing sessions Selenium impossible (non bloquant): {}", ex.getMessage());
        }
    }

    private void verifyRemoteSeleniumReady() {
        String statusUrl = buildSeleniumStatusUrl(properties.getRemoteWebDriverUrl());
        long timeoutSeconds = Math.max(5, properties.getSeleniumReadinessTimeoutSeconds());
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        Exception lastFailure = null;

        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

        while (Instant.now().isBefore(deadline)) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(statusUrl))
                        .GET().timeout(Duration.ofSeconds(3)).build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                String body = response.body() == null ? "" : response.body();
                String normalized = body.replace(" ", "").replace("\n", "").replace("\r", "").toLowerCase();
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    boolean ready = normalized.contains("\"ready\":true");
                    boolean hasNodes = normalized.contains("\"nodes\":[{");
                    if (ready || hasNodes) {
                        return;
                    }
                }
                lastFailure = new IllegalStateException("status=" + response.statusCode());
            } catch (Exception ex) {
                lastFailure = ex;
            }
            sleepQuietly(400);
        }

        String rootCause = lastFailure != null ? loginHelper.sanitize(lastFailure.getMessage()) : "Timeout /status";
        throw new PartslinkSeleniumStartupException(
                "Selenium Grid indisponible sur " + statusUrl + ". Cause: " + rootCause);
    }

    private String buildGridBaseUrl(String remoteWebDriverUrl) {
        String base = remoteWebDriverUrl == null ? "" : remoteWebDriverUrl.trim();
        if (base.endsWith("/wd/hub")) {
            return base.substring(0, base.length() - "/wd/hub".length());
        }
        if (base.endsWith("/wd/hub/")) {
            return base.substring(0, base.length() - "/wd/hub/".length());
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String buildSeleniumStatusUrl(String remoteWebDriverUrl) {
        return buildGridBaseUrl(remoteWebDriverUrl) + "/status";
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interruption pendant l'attente Selenium", ie);
        }
    }
}
