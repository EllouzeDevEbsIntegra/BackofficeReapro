package com.reapro.achat.partslink;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Une session navigateur Partslink <b>isolée</b> : un {@link RemoteWebDriver} dédié,
 * son propre verrou, son propre état, son identifiant de slot et ses timestamps.
 *
 * <p>Aucun état navigateur n'est partagé entre utilisateurs : chaque slot du
 * {@link PartslinkSessionPool} possède sa propre instance. Le pool garantit qu'un
 * slot n'est prêté qu'à un seul appelant à la fois (pas de tabs partagés, pas de
 * réutilisation sale du contexte VIN).</p>
 */
@Slf4j
public class PartslinkBrowserSession {

    private final int slotIndex;
    private final RemoteWebDriver driver;
    private final PartslinkLoginHelper loginHelper;
    private final ReentrantLock lock = new ReentrantLock();
    private final Instant createdAt = Instant.now();

    private volatile Instant lastActivityAt = Instant.now();

    public PartslinkBrowserSession(int slotIndex, RemoteWebDriver driver, PartslinkLoginHelper loginHelper) {
        this.slotIndex = slotIndex;
        this.driver = driver;
        this.loginHelper = loginHelper;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public RemoteWebDriver getDriver() {
        return driver;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markActivity() {
        this.lastActivityAt = Instant.now();
    }

    /** Le navigateur distant répond-il encore ? */
    public boolean isAlive() {
        lock.lock();
        try {
            if (driver == null) {
                return false;
            }
            driver.getCurrentUrl();
            return true;
        } catch (WebDriverException ex) {
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Prépare le slot avant de le prêter à un job : on nettoie le contexte du job
     * précédent (about:blank → aucune fuite de VIN), puis on s'assure que la session
     * est toujours authentifiée (relogin transparent si nécessaire).
     */
    public void prepareForLease() {
        lock.lock();
        try {
            loginHelper.resetContext(driver);
            if (loginHelper.isLoginPage(driver)) {
                log.info("[Partslink][slot {}] session expirée, relogin transparent.", slotIndex);
                loginHelper.login(driver);
            }
            markActivity();
        } finally {
            lock.unlock();
        }
    }

    /** Ferme proprement le navigateur de ce slot. */
    public void close() {
        lock.lock();
        try {
            if (driver != null) {
                driver.quit();
            }
        } catch (Exception ex) {
            log.debug("[Partslink][slot {}] erreur ignorée à la fermeture: {}", slotIndex, ex.getMessage());
        } finally {
            lock.unlock();
        }
    }
}
