package com.reapro.achat.partslink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Logique de connexion Partslink24, rendue STATELESS : elle opère sur un
 * {@link RemoteWebDriver} fourni, sans aucun état partagé. Cela permet à chaque
 * session isolée du pool de se connecter sur son propre navigateur.
 *
 * <p>Extrait de l'ancien {@code PartslinkSessionService} (qui mélangeait login et
 * état global d'un WebDriver unique).</p>
 *
 * <p>Aucun mot de passe / token n'est jamais loggé : {@link #sanitize(String)} masque
 * les credentials dans tout message d'erreur propagé.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartslinkLoginHelper {

    private final PartslinkProperties properties;

    public void validateCredentialsConfigured() {
        if (!StringUtils.hasText(properties.getUsername()) || !StringUtils.hasText(properties.getPassword())) {
            throw new IllegalStateException(
                    "Identifiants Partslink non configurés. Définir PARTSLINK_USERNAME / PARTSLINK_PASSWORD.");
        }
    }

    /**
     * Connecte le navigateur fourni à Partslink24. No-op si déjà authentifié.
     */
    public void login(RemoteWebDriver driver) {
        if (driver == null) {
            throw new IllegalStateException("WebDriver non initialisé pour le login Partslink.");
        }
        validateCredentialsConfigured();

        driver.navigate().to(properties.getLoginUrl());
        resolveStartupAttentionPageIfPresent(driver);

        if (!isLoginPage(driver)) {
            return; // déjà connecté (cookies de session réutilisés)
        }

        waitForElementOrTimeout(driver, Duration.ofSeconds(45),
                "input#login-id",
                "input[name='accountLogin']",
                "input#login-name",
                "input[name='userLogin']",
                "input#inputPassword",
                "input[name='loginBean.password']"
        );

        WebElement accountInput = findFirstElement(driver, "input#login-id", "input[name='accountLogin']");
        WebElement usernameInput = findFirstElement(driver, "input#login-name", "input[name='userLogin']");
        WebElement passwordInput = findFirstElement(driver,
                "input#inputPassword", "input[name='loginBean.password']", "input[type='password']");

        if (usernameInput == null || passwordInput == null) {
            throw new PartslinkLoginException("Impossible de localiser les champs de login Partslink.");
        }

        if (accountInput != null && StringUtils.hasText(properties.getCompanyId())) {
            setFieldValue(driver, accountInput, properties.getCompanyId());
        }
        setFieldValue(driver, usernameInput, properties.getUsername());
        setFieldValue(driver, passwordInput, properties.getPassword());

        WebElement submitButton = findFirstElement(driver,
                "a#login-btn",
                "#hidden-login",
                "button[type='submit']",
                "input[type='submit']",
                "button[name='login']",
                "button[id='loginButton']");
        if (submitButton == null) {
            throw new PartslinkLoginException("Impossible de localiser le bouton de connexion Partslink.");
        }

        clickElementRobust(driver, submitButton);

        if (isLoginPage(driver)) {
            try {
                driver.executeScript("if (typeof doLoginAjax === 'function') { doLoginAjax(false); }");
            } catch (Exception ignored) {
                // chemin clic standard si le script n'est pas disponible
            }
        }

        waitForLoginResult(driver, Duration.ofSeconds(45));
    }

    /**
     * Réinitialise le contexte du navigateur entre deux jobs : on quitte la page
     * du VIN précédent (about:blank) pour qu'aucun état véhicule/recherche ne fuite
     * vers le job/utilisateur suivant. La session (cookies) reste valide.
     */
    public void resetContext(RemoteWebDriver driver) {
        if (driver == null) {
            return;
        }
        try {
            driver.switchTo().defaultContent();
            driver.navigate().to("about:blank");
        } catch (Exception ex) {
            log.debug("[Partslink] reset context ignoré: {}", ex.getMessage());
        }
    }

    public boolean isLoginPage(RemoteWebDriver driver) {
        if (driver == null) {
            return false;
        }
        String currentUrl = safeCurrentUrl(driver);
        if (currentUrl != null) {
            String lower = currentUrl.toLowerCase();
            if (lower.contains("/pl24-app/") || lower.contains("/partslink24/startup.do") || lower.contains("/r/start")) {
                return false;
            }
            if (lower.contains("login")) {
                return true;
            }
        }
        return findFirstElement(driver,
                "input#login-id",
                "input[name='accountLogin']",
                "input#login-name",
                "input[name='userLogin']",
                "input#inputPassword",
                "input[name='loginBean.password']",
                "input[type='password']") != null;
    }

    /** Masque username/password dans un message destiné aux logs / à l'API. */
    public String sanitize(String raw) {
        if (raw == null) {
            return "Erreur inconnue";
        }
        String sanitized = raw;
        if (StringUtils.hasText(properties.getPassword())) {
            sanitized = sanitized.replace(properties.getPassword(), "****");
        }
        if (StringUtils.hasText(properties.getUsername())) {
            sanitized = sanitized.replace(properties.getUsername(), "****");
        }
        return sanitized;
    }

    // --- helpers (portés depuis l'ancien PartslinkSessionService) ---

    private void waitForLoginResult(RemoteWebDriver driver, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (!isLoginPage(driver)) {
                return;
            }
            if (tryHandleConcurrentSessionPrompt(driver)) {
                sleepQuietly(300);
                continue;
            }
            String loginError = readLoginError(driver);
            if (StringUtils.hasText(loginError)) {
                throw new PartslinkLoginException("Échec de connexion Partslink: " + loginError);
            }
            sleepQuietly(300);
        }
        throw new PartslinkLoginException(
                "Timeout de connexion Partslink. Vérifier identifiants, sélecteurs ou page de challenge.");
    }

    private boolean tryHandleConcurrentSessionPrompt(RemoteWebDriver driver) {
        WebElement squeezeOutConfirm = findFirstElement(driver, "#squeezeout-login-btn", "a#squeezeout-login-btn");
        if (squeezeOutConfirm == null) {
            return false;
        }
        try {
            if (!squeezeOutConfirm.isDisplayed()) {
                return false;
            }
        } catch (Exception ignored) {
            return false;
        }
        clickElementRobust(driver, squeezeOutConfirm);
        return true;
    }

    private String readLoginError(RemoteWebDriver driver) {
        WebElement errorDiv = findFirstElement(driver, "#loginErrorDiv", "#sessionCheckError");
        if (errorDiv == null) {
            return null;
        }
        try {
            String txt = errorDiv.getText();
            if (StringUtils.hasText(txt)) {
                return txt.trim();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private void resolveStartupAttentionPageIfPresent(RemoteWebDriver driver) {
        String currentUrl = safeCurrentUrl(driver);
        String title;
        try {
            title = driver.getTitle();
        } catch (Exception ex) {
            title = null;
        }
        if ((title != null && title.toLowerCase().contains("attention"))
                || (currentUrl != null && currentUrl.toLowerCase().contains("startup.do"))) {
            WebElement reloadLink = findFirstElement(driver,
                    "a[href*='startup.do']", "a[href*='partslink24/startup.do']");
            if (reloadLink != null) {
                clickElementRobust(driver, reloadLink);
                waitForElementOrTimeout(driver, Duration.ofSeconds(15),
                        "input#login-id", "input[name='accountLogin']", "input#inputPassword");
            }
        }
    }

    private void setFieldValue(RemoteWebDriver driver, WebElement element, String value) {
        if (element == null || value == null) {
            return;
        }
        try {
            element.click();
            element.sendKeys(Keys.chord(Keys.CONTROL, "a"));
            element.sendKeys(Keys.DELETE);
            element.sendKeys(value);
        } catch (WebDriverException ex) {
            driver.executeScript("arguments[0].value = arguments[1];", element, value);
        }
    }

    private void clickElementRobust(RemoteWebDriver driver, WebElement element) {
        if (element == null) {
            return;
        }
        try {
            element.click();
        } catch (WebDriverException ex) {
            driver.executeScript("arguments[0].click();", element);
        }
    }

    private String safeCurrentUrl(RemoteWebDriver driver) {
        try {
            return driver != null ? driver.getCurrentUrl() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private WebElement findFirstElement(RemoteWebDriver driver, String... cssSelectors) {
        if (driver == null) {
            return null;
        }
        for (String selector : cssSelectors) {
            List<WebElement> elements = driver.findElements(By.cssSelector(selector));
            if (!elements.isEmpty()) {
                return elements.get(0);
            }
        }
        return null;
    }

    private void waitForElementOrTimeout(RemoteWebDriver driver, Duration timeout, String... cssSelectors) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (findFirstElement(driver, cssSelectors) != null) {
                return;
            }
            sleepQuietly(300);
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interruption pendant l'attente Partslink", ie);
        }
    }
}
