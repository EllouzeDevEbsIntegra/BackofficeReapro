package com.reapro.achat.partslink;

import com.reapro.achat.entities.primary.PartslinkGroup;
import com.reapro.achat.entities.primary.PartslinkPart;
import com.reapro.achat.entities.primary.PartslinkSchematic;
import com.reapro.achat.entities.primary.PartslinkSubgroup;
import com.reapro.achat.entities.primary.PartslinkVehicle;
import com.reapro.achat.repositories.primary.PartslinkGroupRepository;
import com.reapro.achat.repositories.primary.PartslinkPartRepository;
import com.reapro.achat.repositories.primary.PartslinkSchematicRepository;
import com.reapro.achat.repositories.primary.PartslinkSubgroupRepository;
import com.reapro.achat.repositories.primary.PartslinkVehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests du cache Partslink : self-heal brand_code, refresh ciblé sur groupe parent absent,
 * erreur métier propre, cache hit sans Selenium, normalisation VIN (pas de mélange).
 */
class PartslinkCacheServiceTest {

    private static final String VIN = "WBA8C31070K769705";

    private PartslinkVehicleRepository vehicleRepo;
    private PartslinkGroupRepository groupRepo;
    private PartslinkSubgroupRepository subgroupRepo;
    private PartslinkPartRepository partRepo;
    private PartslinkSchematicRepository schematicRepo;
    private PartslinkScraperService scraper;
    private PartslinkSessionPool pool;
    private PartslinkCacheService cache;

    @BeforeEach
    void setUp() {
        vehicleRepo = mock(PartslinkVehicleRepository.class);
        groupRepo = mock(PartslinkGroupRepository.class);
        subgroupRepo = mock(PartslinkSubgroupRepository.class);
        partRepo = mock(PartslinkPartRepository.class);
        schematicRepo = mock(PartslinkSchematicRepository.class);
        scraper = mock(PartslinkScraperService.class);
        pool = mock(PartslinkSessionPool.class);
        cache = new PartslinkCacheService(vehicleRepo, groupRepo, subgroupRepo, partRepo, schematicRepo, scraper, pool);
    }

    /** Fait exécuter la lambda passée au pool (driver = null, le scraper est mocké). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void poolExecutesLambda() {
        when(pool.withLeasedDriver(any())).thenAnswer(inv -> ((Function) inv.getArgument(0)).apply(null));
    }

    private PartslinkVehicle vehicle(Long id, String brand) {
        return PartslinkVehicle.builder().id(id).vin(VIN).model("M").brandCode(brand).build();
    }

    private PartslinkScraperService.ScrapedVehicleDetails details(String brand) {
        return new PartslinkScraperService.ScrapedVehicleDetails(
                VIN, "Serie 8", "2019", "Noir", "Cuir", "Auto", "MC", "Serie 8", brand,
                List.of(new PartslinkScraperService.ScrapedGroup("24", "Boite auto")));
    }

    // --- Cache hit : aucun Selenium réservé ---
    @Test
    void subgroupsCacheHit_doesNotUsePool() {
        PartslinkVehicle v = vehicle(1L, "bmw_parts");
        PartslinkGroup g = PartslinkGroup.builder().id(10L).vehicle(v).code("24").name("Boite").build();
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(v));
        when(groupRepo.findByVehicleAndCode(v, "24")).thenReturn(Optional.of(g));
        when(subgroupRepo.findByGroup(g)).thenReturn(List.of(
                PartslinkSubgroup.builder().id(100L).group(g).code("24_1").name("Convertisseur").build()));

        List<PartslinkScraperService.ScrapedSubgroup> r = cache.getOrFetchSubgroups(VIN, "24");

        assertThat(r).hasSize(1);
        verify(pool, never()).withLeasedDriver(any());
    }

    // --- Parent absent + marque connue → refresh ciblé puis succès ---
    @Test
    void parentGroupMissing_withBrand_triggersTargetedRefresh() {
        PartslinkVehicle v = vehicle(1L, "bmw_parts");
        PartslinkGroup g = PartslinkGroup.builder().id(10L).vehicle(v).code("24").name("Boite").build();
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(v));
        // 1er appel : parent absent → refresh ; 2e appel (après refresh) : présent
        when(groupRepo.findByVehicleAndCode(v, "24")).thenReturn(Optional.empty(), Optional.of(g));
        when(groupRepo.findByVehicle(v)).thenReturn(List.of()); // refresh persiste les groupes
        when(scraper.identifyVehicleAndGroups(any(), eq(VIN), eq("bmw_parts"), any())).thenReturn(details("bmw_parts"));
        when(subgroupRepo.findByGroup(g)).thenReturn(List.of());
        when(scraper.fetchSubgroups(any(), eq(VIN), eq("24"), eq("bmw_parts")))
                .thenReturn(List.of(new PartslinkScraperService.ScrapedSubgroup("24_1", "Convertisseur")));
        poolExecutesLambda();

        List<PartslinkScraperService.ScrapedSubgroup> r = cache.getOrFetchSubgroups(VIN, "24");

        assertThat(r).hasSize(1);
        verify(scraper).identifyVehicleAndGroups(any(), eq(VIN), eq("bmw_parts"), any()); // refresh exécuté
    }

    // --- Parent absent + marque inconnue → erreur métier propre (pas de stack brute) ---
    @Test
    void parentGroupMissing_withoutBrand_throwsCleanGroupNotFound() {
        PartslinkVehicle v = vehicle(1L, null); // brand_code manquant (vieille ligne pré-V7)
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(v));
        when(groupRepo.findByVehicleAndCode(v, "24")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cache.getOrFetchSubgroups(VIN, "24"))
                .isInstanceOf(PartslinkGroupNotFoundException.class);
        verify(scraper, never()).identifyVehicleAndGroups(any(), any(), any(), any());
    }

    // --- saveVehicleAndGroups : backfill brand_code non destructif si vide ---
    @Test
    void saveVehicleAndGroups_backfillsBrandCodeWhenBlank() {
        PartslinkVehicle existing = vehicle(1L, null);
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(existing));
        when(groupRepo.findByVehicle(existing)).thenReturn(List.of(
                PartslinkGroup.builder().id(10L).vehicle(existing).code("24").name("Boite").build()));

        cache.saveVehicleAndGroups(details("bmw_parts"));

        assertThat(existing.getBrandCode()).isEqualTo("bmw_parts");
        verify(vehicleRepo).save(existing);
    }

    // --- saveVehicleAndGroups : backfill des groupes si véhicule présent sans groupes ---
    @Test
    void saveVehicleAndGroups_backfillsGroupsWhenMissing() {
        PartslinkVehicle existing = vehicle(1L, "bmw_parts");
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(existing));
        when(groupRepo.findByVehicle(existing)).thenReturn(List.of());

        cache.saveVehicleAndGroups(details("bmw_parts"));

        verify(groupRepo, times(1)).save(any(PartslinkGroup.class));
    }

    // --- Normalisation VIN : pas de mélange (lookup toujours en MAJUSCULES/trim) ---
    @Test
    void vinIsNormalizedOnLookup() {
        PartslinkVehicle v = vehicle(1L, "bmw_parts");
        PartslinkGroup g = PartslinkGroup.builder().id(10L).vehicle(v).code("24").name("Boite").build();
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(v));
        when(groupRepo.findByVehicleAndCode(v, "24")).thenReturn(Optional.of(g));
        when(subgroupRepo.findByGroup(g)).thenReturn(List.of(
                PartslinkSubgroup.builder().id(100L).group(g).code("24_1").name("x").build()));

        cache.getOrFetchSubgroups("  wba8c31070k769705  ", "24");

        verify(vehicleRepo).findByVin(VIN); // trimmé + majuscules
    }

    // ===================== DÉTAILS (schéma + pièces) =====================

    private PartslinkSubgroup subgroupWithParent(PartslinkVehicle v, PartslinkGroup parent, String code) {
        return PartslinkSubgroup.builder().id(100L).group(parent).code(code).name("Convertisseur").build();
    }

    @Test
    void detailsCacheHit_doesNotUsePool() {
        PartslinkVehicle v = vehicle(1L, "bmw_parts");
        PartslinkGroup parent = PartslinkGroup.builder().id(10L).vehicle(v).code("24").name("Boite").build();
        PartslinkSubgroup sg = subgroupWithParent(v, parent, "24_1");
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(v));
        when(groupRepo.findByVehicle(v)).thenReturn(List.of(parent));
        when(subgroupRepo.findByGroupAndCode(parent, "24_1")).thenReturn(Optional.of(sg));
        when(schematicRepo.findBySubgroup(sg)).thenReturn(Optional.of(
                PartslinkSchematic.builder().id(1L).subgroup(sg).imagePath("/uploads/partslink/24_1.png").build()));
        when(partRepo.findBySubgroup(sg)).thenReturn(List.of(
                PartslinkPart.builder().id(1L).subgroup(sg).position("1").partNumber("11111").designation("Vis").quantity("2").build()));

        PartslinkScraperService.ScrapedSubgroupDetails d = cache.getOrFetchSubgroupDetails(VIN, "24_1");

        assertThat(d.imagePath()).isEqualTo("/uploads/partslink/24_1.png");
        assertThat(d.parts()).hasSize(1);
        verify(pool, never()).withLeasedDriver(any());
    }

    @Test
    void detailsCacheMiss_scrapesWithParentGroupAndBrand_thenPersists() {
        PartslinkVehicle v = vehicle(1L, "bmw_parts");
        PartslinkGroup parent = PartslinkGroup.builder().id(10L).vehicle(v).code("24").name("Boite").build();
        PartslinkSubgroup sg = subgroupWithParent(v, parent, "24_1");
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(v));
        when(groupRepo.findByVehicle(v)).thenReturn(List.of(parent));
        when(subgroupRepo.findByGroupAndCode(parent, "24_1")).thenReturn(Optional.of(sg));
        when(schematicRepo.findBySubgroup(sg)).thenReturn(Optional.empty());
        when(partRepo.findBySubgroup(sg)).thenReturn(List.of());
        when(scraper.fetchPartsAndSchematic(any(), eq(VIN), eq("bmw_parts"), eq("24"), eq("24_1")))
                .thenReturn(new PartslinkScraperService.ScrapedSubgroupDetails("/uploads/partslink/24_1.png",
                        List.of(new PartslinkScraperService.ScrapedPart("1", "11111", "Vis", "", "2", ""))));
        poolExecutesLambda();

        PartslinkScraperService.ScrapedSubgroupDetails d = cache.getOrFetchSubgroupDetails(VIN, "24_1");

        assertThat(d.parts()).hasSize(1);
        verify(scraper).fetchPartsAndSchematic(any(), eq(VIN), eq("bmw_parts"), eq("24"), eq("24_1"));
        verify(partRepo, times(1)).save(any(PartslinkPart.class));
        verify(schematicRepo, times(1)).save(any(PartslinkSchematic.class));
    }

    @Test
    void detailsWithSchematicButNoParts_returnsEmptyNotError() {
        PartslinkVehicle v = vehicle(1L, "bmw_parts");
        PartslinkGroup parent = PartslinkGroup.builder().id(10L).vehicle(v).code("24").name("Boite").build();
        PartslinkSubgroup sg = subgroupWithParent(v, parent, "24_1");
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(v));
        when(groupRepo.findByVehicle(v)).thenReturn(List.of(parent));
        when(subgroupRepo.findByGroupAndCode(parent, "24_1")).thenReturn(Optional.of(sg));
        when(schematicRepo.findBySubgroup(sg)).thenReturn(Optional.empty());
        when(partRepo.findBySubgroup(sg)).thenReturn(List.of());
        when(scraper.fetchPartsAndSchematic(any(), eq(VIN), eq("bmw_parts"), eq("24"), eq("24_1")))
                .thenReturn(new PartslinkScraperService.ScrapedSubgroupDetails("/uploads/partslink/24_1.png", List.of()));
        poolExecutesLambda();

        PartslinkScraperService.ScrapedSubgroupDetails d = cache.getOrFetchSubgroupDetails(VIN, "24_1");

        assertThat(d.parts()).isEmpty();
        assertThat(d.imagePath()).isNotNull();
    }

    @Test
    void detailsScrapeFailsAfterRetry_throwsCleanGroupNotFound() {
        PartslinkVehicle v = vehicle(1L, "bmw_parts");
        PartslinkGroup parent = PartslinkGroup.builder().id(10L).vehicle(v).code("24").name("Boite").build();
        PartslinkSubgroup sg = subgroupWithParent(v, parent, "24_1");
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(v));
        when(groupRepo.findByVehicle(v)).thenReturn(List.of(parent));
        when(subgroupRepo.findByGroupAndCode(parent, "24_1")).thenReturn(Optional.of(sg));
        when(schematicRepo.findBySubgroup(sg)).thenReturn(Optional.empty());
        when(partRepo.findBySubgroup(sg)).thenReturn(List.of());
        when(scraper.fetchPartsAndSchematic(any(), eq(VIN), eq("bmw_parts"), eq("24"), eq("24_1")))
                .thenThrow(new IllegalStateException("DOM introuvable"));
        poolExecutesLambda();

        assertThatThrownBy(() -> cache.getOrFetchSubgroupDetails(VIN, "24_1"))
                .isInstanceOf(PartslinkGroupNotFoundException.class);
        verify(scraper, times(2)).fetchPartsAndSchematic(any(), eq(VIN), eq("bmw_parts"), eq("24"), eq("24_1"));
    }

    @Test
    void detailsSubgroupNotCached_scrapesDirectWithoutParent() {
        PartslinkVehicle v = vehicle(1L, "bmw_parts");
        PartslinkGroup parent = PartslinkGroup.builder().id(10L).vehicle(v).code("24").name("Boite").build();
        when(vehicleRepo.findByVin(VIN)).thenReturn(Optional.of(v));
        when(groupRepo.findByVehicle(v)).thenReturn(List.of(parent));
        when(subgroupRepo.findByGroupAndCode(parent, "99_9")).thenReturn(Optional.empty());
        when(scraper.fetchPartsAndSchematic(any(), eq(VIN), eq("bmw_parts"), eq(null), eq("99_9")))
                .thenReturn(new PartslinkScraperService.ScrapedSubgroupDetails(null, List.of()));
        poolExecutesLambda();

        PartslinkScraperService.ScrapedSubgroupDetails d = cache.getOrFetchSubgroupDetails(VIN, "99_9");

        assertThat(d).isNotNull();
        verify(scraper).fetchPartsAndSchematic(any(), eq(VIN), eq("bmw_parts"), eq(null), eq("99_9"));
    }
}
