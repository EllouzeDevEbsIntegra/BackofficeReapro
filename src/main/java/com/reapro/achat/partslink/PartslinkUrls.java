package com.reapro.achat.partslink;

/**
 * Utilitaires URL Partslink. Pur (testable sans Selenium).
 *
 * <p>Sécurité : certaines URLs Partslink peuvent porter un identifiant de session / token
 * dans la query string. Les dumps de diagnostic et les logs ne doivent JAMAIS exposer la query
 * → {@link #stripQuery(String)} ne conserve que schéma + hôte + chemin.</p>
 */
public final class PartslinkUrls {

    private PartslinkUrls() {
    }

    /** Retourne l'URL sans query string ni fragment (anti-fuite de token en query). */
    public static String stripQuery(String url) {
        if (url == null) {
            return null;
        }
        String base = url;
        int q = base.indexOf('?');
        if (q >= 0) {
            base = base.substring(0, q);
        }
        int h = base.indexOf('#');
        if (h >= 0) {
            base = base.substring(0, h);
        }
        return base;
    }
}
