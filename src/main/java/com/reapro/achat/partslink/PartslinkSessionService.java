package com.reapro.achat.partslink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Façade fine au-dessus du {@link PartslinkSessionPool}.
 *
 * <p>Refonte : l'ancien WebDriver global unique + verrou global + viewer iframe ont été
 * supprimés. Cette classe ne conserve que les opérations exposées par
 * {@code PartslinkViewerController} pour la compatibilité du frontend (statut, warmup),
 * plus les actions d'administration de pool (recycle/close) — sécurisées côté contrôleur.</p>
 *
 * <p>Il n'y a plus d'état navigateur partagé ici : tout est délégué au pool de sessions isolées.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartslinkSessionService {

    private final PartslinkProperties properties;
    private final PartslinkSessionPool pool;

    /** Statut "global" = disponibilité du pool (utilisé par le badge frontend). */
    public PartslinkSessionStatusResponse getSessionStatus() {
        PartslinkSessionPool.PoolStatus status = pool.getStatus();
        boolean enabled = properties.isEnabled() && status.enabled();
        PartslinkSessionState state = enabled ? PartslinkSessionState.ACTIVE : PartslinkSessionState.DISABLED;
        String message = enabled
                ? String.format("Pool Partslink prêt — %d/%d sessions occupées.", status.busy(), status.maxSessions())
                : "Module Partslink désactivé.";
        // active = le module peut servir des recherches (le pool crée les slots à la demande).
        return new PartslinkSessionStatusResponse(null, state, enabled, null, Instant.now(), message);
    }

    /**
     * "Démarrage de session" historique → désormais un simple warmup du pool
     * (acquiert puis relâche un slot pour vérifier la connexion). Non destructif.
     */
    public PartslinkSessionResponse createOrGetSession() {
        ensureEnabled();
        try {
            pool.withLeasedDriver(driver -> driver != null ? driver.getCurrentUrl() : null);
            return new PartslinkSessionResponse(null, null, PartslinkSessionState.ACTIVE, "Pool Partslink opérationnel.");
        } catch (RuntimeException ex) {
            log.warn("[Partslink] Warmup pool échoué: {}", ex.getMessage());
            return new PartslinkSessionResponse(null, null, PartslinkSessionState.ERROR, ex.getMessage());
        }
    }

    /** Action ADMIN : recycle tout le pool. */
    public PartslinkSessionResponse restartSession() {
        ensureEnabled();
        pool.recycleAll();
        return new PartslinkSessionResponse(null, null, PartslinkSessionState.ACTIVE, "Pool Partslink recyclé.");
    }

    /** Action ADMIN : ferme tout le pool. */
    public void closeSession() {
        pool.closeAll();
    }

    private void ensureEnabled() {
        if (!(properties.isEnabled() && properties.getPool().isEnabled())) {
            throw new IllegalStateException("Le module Partslink (pool) est désactivé par configuration.");
        }
    }
}
