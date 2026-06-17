package com.reapro.achat.partslink;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Pool de sessions navigateur isolées, borné à {@code partslink.pool.max-sessions}
 * (= limite licence). Remplace l'ancien WebDriver global unique.
 *
 * <ul>
 *   <li>acquisition d'un slot libre, file d'attente FIFO si tous occupés ;</li>
 *   <li>verrou <b>par slot</b> (porté par {@link PartslinkBrowserSession}), pas de verrou global ;</li>
 *   <li>reset du contexte entre jobs → aucune réutilisation sale du VIN ;</li>
 *   <li>nettoyage automatique des slots morts / inactifs ;</li>
 *   <li>aucune donnée utilisateur mélangée (chaque lease = usage exclusif).</li>
 * </ul>
 */
@Service
@Slf4j
public class PartslinkSessionPool {

    private final PartslinkProperties properties;
    private final PartslinkBrowserSessionFactory factory;

    /** Capacité = nombre de leases simultanés autorisés (FIFO → 6e en attente). */
    private final Semaphore capacity;
    /** Sessions créées et disponibles (non prêtées). */
    private final LinkedBlockingDeque<PartslinkBrowserSession> idle = new LinkedBlockingDeque<>();
    /** Toutes les sessions vivantes connues (prêtées + idle), pour status/cleanup. */
    private final List<PartslinkBrowserSession> all = new ArrayList<>();
    private final Object allLock = new Object();

    private final AtomicInteger createdCount = new AtomicInteger(0);
    private final AtomicInteger leasedCount = new AtomicInteger(0);
    private final AtomicInteger slotSequence = new AtomicInteger(0);
    private volatile boolean orphansCleaned = false;
    /** Sessions créées avant cet instant sont recyclées à leur libération (recycle/close admin). */
    private volatile Instant recycleBefore = Instant.EPOCH;

    public PartslinkSessionPool(PartslinkProperties properties, PartslinkBrowserSessionFactory factory) {
        this.properties = properties;
        this.factory = factory;
        int max = Math.max(1, properties.getPool().getMaxSessions());
        this.capacity = new Semaphore(max, true); // fair → file d'attente FIFO
    }

    public boolean isEnabled() {
        return properties.isEnabled() && properties.getPool().isEnabled();
    }

    public int getMaxSessions() {
        return Math.max(1, properties.getPool().getMaxSessions());
    }

    /**
     * Emprunte un slot, exécute l'action avec le driver isolé, puis libère le slot.
     * C'est l'unique point d'accès au navigateur : il borne strictement la concurrence à max-sessions.
     */
    public <T> T withLeasedDriver(Function<org.openqa.selenium.remote.RemoteWebDriver, T> action) {
        PartslinkBrowserSession session = lease();
        try {
            T result = action.apply(session.getDriver());
            session.markActivity();
            return result;
        } finally {
            release(session);
        }
    }

    /**
     * Acquiert un slot libre (en créant la session si nécessaire, jusqu'à max).
     * Bloque jusqu'à acquire-timeout si les {@code max} slots sont occupés, puis lève
     * {@link PartslinkPoolBusyException}.
     */
    public PartslinkBrowserSession lease() {
        if (!isEnabled()) {
            throw new IllegalStateException("Le pool Partslink est désactivé (partslink.pool.enabled=false).");
        }
        long timeout = Math.max(1, properties.getPool().getAcquireTimeoutSeconds());
        boolean acquired;
        try {
            acquired = capacity.tryAcquire(timeout, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new PartslinkPoolBusyException("Interruption pendant l'attente d'une session Partslink.");
        }
        if (!acquired) {
            throw new PartslinkPoolBusyException(
                    "Toutes les sessions Partslink sont occupées (" + getMaxSessions()
                            + " max). Réessayez dans un instant.");
        }

        // À partir d'ici on détient un permis : on doit fournir une session OU relâcher le permis.
        RuntimeException lastError = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            PartslinkBrowserSession session = idle.poll();
            boolean freshlyCreated = false;
            try {
                if (session == null) {
                    cleanupOrphansOnceIfNeeded();
                    session = factory.create(slotSequence.incrementAndGet());
                    freshlyCreated = true;
                    register(session);
                }
                if (!session.isAlive()) {
                    discard(session);
                    session = null;
                    continue;
                }
                session.prepareForLease();
                leasedCount.incrementAndGet();
                return session;
            } catch (RuntimeException ex) {
                lastError = ex;
                if (session != null) {
                    discard(session);
                }
                if (freshlyCreated) {
                    // échec de création/login : ne pas boucler indéfiniment
                    break;
                }
            }
        }

        capacity.release();
        if (lastError instanceof PartslinkLoginException || lastError instanceof PartslinkSeleniumStartupException) {
            throw lastError;
        }
        throw new PartslinkPoolBusyException(
                "Impossible d'obtenir une session Partslink valide" +
                        (lastError != null ? ": " + lastError.getMessage() : "."));
    }

    /** Rend un slot au pool (ou le détruit s'il est mort / marqué pour recyclage). */
    public void release(PartslinkBrowserSession session) {
        if (session == null) {
            return;
        }
        try {
            leasedCount.decrementAndGet();
            boolean recycle = session.getCreatedAt().isBefore(recycleBefore);
            if (!recycle && session.isAlive()) {
                session.markActivity();
                idle.offer(session);
            } else {
                discard(session);
            }
        } finally {
            capacity.release();
        }
    }

    private void register(PartslinkBrowserSession session) {
        createdCount.incrementAndGet();
        synchronized (allLock) {
            all.add(session);
        }
    }

    private void discard(PartslinkBrowserSession session) {
        synchronized (allLock) {
            all.remove(session);
        }
        createdCount.decrementAndGet();
        try {
            session.close();
        } catch (Exception ignored) {
            // best effort
        }
    }

    private void cleanupOrphansOnceIfNeeded() {
        if (orphansCleaned) {
            return;
        }
        synchronized (this) {
            if (orphansCleaned) {
                return;
            }
            try {
                factory.cleanupOrphansOnce();
            } catch (Exception ex) {
                log.warn("[Partslink] cleanup orphelines ignoré: {}", ex.getMessage());
            }
            orphansCleaned = true;
        }
    }

    /** Recyclage de tout le pool (action admin). Les slots prêtés sont recyclés à leur libération. */
    public synchronized void recycleAll() {
        recycleBefore = Instant.now();
        List<PartslinkBrowserSession> snapshot;
        synchronized (allLock) {
            snapshot = new ArrayList<>(all);
        }
        for (PartslinkBrowserSession s : snapshot) {
            if (idle.remove(s)) {
                discard(s);
            }
        }
        log.info("[Partslink] Pool recyclé (admin). Slots idle fermés, slots actifs recyclés à la libération.");
    }

    /** Ferme tout le pool (action admin). */
    public synchronized void closeAll() {
        recycleAll();
    }

    /** Nettoyage périodique des slots inactifs ou morts. Ne touche que les slots idle (non prêtés). */
    @Scheduled(fixedDelayString = "${partslink.heartbeat-interval-ms:120000}")
    public void reapIdleSessions() {
        if (!isEnabled()) {
            return;
        }
        long idleMinutes = Math.max(1, properties.getPool().getIdleTimeoutMinutes());
        Instant idleDeadline = Instant.now().minus(Duration.ofMinutes(idleMinutes));

        int drained = idle.size();
        List<PartslinkBrowserSession> keep = new ArrayList<>();
        for (int i = 0; i < drained; i++) {
            PartslinkBrowserSession s = idle.poll();
            if (s == null) {
                break;
            }
            boolean dead = !s.isAlive();
            boolean tooOld = s.getLastActivityAt().isBefore(idleDeadline);
            boolean recycle = s.getCreatedAt().isBefore(recycleBefore);
            if (dead || tooOld || recycle) {
                log.info("[Partslink] Reap slot {} (dead={}, idle={}, recycle={}).",
                        s.getSlotIndex(), dead, tooOld, recycle);
                discard(s);
            } else {
                keep.add(s);
            }
        }
        keep.forEach(idle::offer);
    }

    public PoolStatus getStatus() {
        return new PoolStatus(
                isEnabled(),
                getMaxSessions(),
                createdCount.get(),
                leasedCount.get(),
                idle.size()
        );
    }

    public record PoolStatus(boolean enabled, int maxSessions, int created, int busy, int idle) {
        public boolean hasFreeCapacity() {
            return enabled && busy < maxSessions;
        }
    }
}
