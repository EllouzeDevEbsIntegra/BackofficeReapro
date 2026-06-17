package com.reapro.achat.partslink;

/**
 * Levée quand, après recherche VIN, Partslink n'aboutit pas à une page véhicule exploitable
 * (groupes principaux présents). Évite de prendre l'accueil pour un véhicule (faux positif
 * historique sur {@code mainContainer}). Le contrôleur/job la transforme en message métier clair.
 */
public class PartslinkVehicleNotIdentifiedException extends RuntimeException {

    private final String vin;
    private final String brand;
    private final String pageState;

    public PartslinkVehicleNotIdentifiedException(String vin, String brand, String pageState) {
        super(friendlyMessage(pageState));
        this.vin = vin;
        this.brand = brand;
        this.pageState = pageState;
    }

    private static String friendlyMessage(String pageState) {
        if (pageState == null) {
            return "Véhicule non identifié sur Partslink. Vérifiez le VIN puis réessayez.";
        }
        switch (pageState) {
            case "HOME":
                return "Véhicule non chargé (page d'accueil Partslink). Vérifiez le VIN puis réessayez.";
            case "NO_RESULT":
                return "VIN introuvable sur Partslink. Vérifiez le numéro de châssis.";
            case "VEHICLE_SELECTION":
                return "Sélection du véhicule Partslink non aboutie. Réessayez.";
            case "ERROR":
                return "Erreur Partslink lors de l'identification du véhicule. Réessayez.";
            default:
                return "Véhicule non identifié sur Partslink. Vérifiez le VIN puis réessayez.";
        }
    }

    public String getVin() {
        return vin;
    }

    public String getBrand() {
        return brand;
    }

    public String getPageState() {
        return pageState;
    }
}
