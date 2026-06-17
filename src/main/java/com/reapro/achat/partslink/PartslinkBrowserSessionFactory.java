package com.reapro.achat.partslink;

/**
 * Fabrique de sessions navigateur isolées. Abstraite pour permettre l'injection
 * d'une implémentation factice dans les tests (aucun Chrome réel requis).
 */
public interface PartslinkBrowserSessionFactory {

    /** Crée une nouvelle session navigateur isolée, connectée et prête, pour le slot donné. */
    PartslinkBrowserSession create(int slotIndex);

    /**
     * Nettoyage opportuniste des sessions Selenium orphelines laissées par un
     * redémarrage backend précédent. Appelé une seule fois avant la première création.
     * No-op possible.
     */
    default void cleanupOrphansOnce() {
        // implémentation par défaut : rien
    }
}
