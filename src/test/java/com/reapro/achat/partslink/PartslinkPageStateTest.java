package com.reapro.achat.partslink;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du classement d'état de page après recherche VIN (logique PURE, sans Selenium).
 * Garantit notamment que "Page d'accueil" n'est PLUS pris pour un véhicule valide (bug Mercedes),
 * et que le happy path BMW (groupes présents) reste VEHICLE_PAGE.
 */
class PartslinkPageStateTest {

    private static final String HOME_URL = "https://www.partslink24.com/pl24-app/mercedes_parts/0/0?desktop=true&lang=fr";
    private static final String VEHICLE_URL = "https://www.partslink24.com/pl24-app/bmw_parts/WBA11AP000CG41970/0/vehicle?desktop=true&lang=fr";

    @Test
    void mainContainerPageAccueil_isNotAVehicle() {
        // Cas Mercedes réel : mainContainer="Page d'accueil", 0 ligne de groupe → HOME, pas VEHICLE_PAGE
        String state = PartslinkScraperService.classifyPageState(
                0, HOME_URL, "Partslink24", "Page d'accueil", false, false);
        assertThat(state).isEqualTo("HOME");
        assertThat(state).isNotEqualTo("VEHICLE_PAGE");
    }

    @Test
    void groupRowsPresent_isVehiclePage_evenIfTextLooksLikeHome() {
        // Le signal "groupes présents" prime (BMW happy path non régressé)
        String state = PartslinkScraperService.classifyPageState(
                12, VEHICLE_URL, "Partslink24", "accueil blabla", false, false);
        assertThat(state).isEqualTo("VEHICLE_PAGE");
    }

    @Test
    void noGroupsAndErrorIndicator_isError() {
        String state = PartslinkScraperService.classifyPageState(
                0, "https://www.partslink24.com/pl24-app/bmw_parts/x/y", "Partslink24", "", true, false);
        assertThat(state).isEqualTo("ERROR");
    }

    @Test
    void noGroupsButBrandHomeUrl_isHome() {
        String state = PartslinkScraperService.classifyPageState(
                0, HOME_URL, "", "", false, false);
        assertThat(state).isEqualTo("HOME");
    }

    @Test
    void noGroupsWithSelectionList_isVehicleSelection() {
        String state = PartslinkScraperService.classifyPageState(
                0, "https://www.partslink24.com/pl24-app/audi_parts/results", "Résultats", "Choisir un véhicule", false, true);
        assertThat(state).isEqualTo("VEHICLE_SELECTION");
    }

    @Test
    void noGroupsNothingMeaningful_isNoResult() {
        String state = PartslinkScraperService.classifyPageState(
                0, "https://www.partslink24.com/pl24-app/audi_parts/search", "x", "rien", false, false);
        assertThat(state).isEqualTo("NO_RESULT");
    }

    @Test
    void vehicleNotIdentifiedException_carriesCleanMessage_noLeak() {
        PartslinkVehicleNotIdentifiedException ex =
                new PartslinkVehicleNotIdentifiedException("WDD2040311A906121", "mercedes_parts", "HOME");
        assertThat(ex.getPageState()).isEqualTo("HOME");
        assertThat(ex.getMessage()).contains("page d'accueil");
        assertThat(ex.getMessage()).doesNotContain("WDD2040311A906121"); // pas de donnée brute inutile
    }
}
