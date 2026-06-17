package com.reapro.achat.partslink;

/**
 * Levée quand aucune session navigateur isolée n'est disponible dans le délai imparti
 * (les {@code partslink.pool.max-sessions} slots sont tous occupés).
 * Permet au contrôleur de répondre proprement "occupé / réessayez" sans 500.
 */
public class PartslinkPoolBusyException extends RuntimeException {
    public PartslinkPoolBusyException(String message) {
        super(message);
    }
}
