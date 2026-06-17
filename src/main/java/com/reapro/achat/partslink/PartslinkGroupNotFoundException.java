package com.reapro.achat.partslink;

/**
 * Levée quand le groupe principal demandé reste introuvable même après un refresh ciblé
 * du véhicule (cache parent incohérent/incomplet). Le contrôleur la mappe en réponse métier
 * claire (HTTP 409 + code GROUP_REFRESH_NEEDED) — jamais une stack technique brute côté UI.
 */
public class PartslinkGroupNotFoundException extends RuntimeException {

    private final String vin;
    private final String groupCode;

    public PartslinkGroupNotFoundException(String vin, String groupCode) {
        super("Données du groupe '" + groupCode + "' indisponibles pour ce véhicule. Rafraîchissez le VIN.");
        this.vin = vin;
        this.groupCode = groupCode;
    }

    public String getVin() {
        return vin;
    }

    public String getGroupCode() {
        return groupCode;
    }
}
