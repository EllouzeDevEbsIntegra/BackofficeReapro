package com.reapro.achat.partslink;

import com.reapro.achat.entities.primary.*;
import com.reapro.achat.repositories.primary.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartslinkCacheService {

    private final PartslinkVehicleRepository vehicleRepository;
    private final PartslinkGroupRepository groupRepository;
    private final PartslinkSubgroupRepository subgroupRepository;
    private final PartslinkPartRepository partRepository;
    private final PartslinkSchematicRepository schematicRepository;
    private final PartslinkScraperService scraperService;
    private final PartslinkSessionPool sessionPool;

    @Transactional(readOnly = true)
    public Optional<PartslinkVehicle> findVehicleByVin(String vin) {
        return vehicleRepository.findByVin(vin.trim().toUpperCase());
    }

    @Transactional(readOnly = true)
    public List<PartslinkGroup> findGroupsByVehicle(PartslinkVehicle vehicle) {
        return groupRepository.findByVehicle(vehicle);
    }

    @Transactional
    public void saveVehicleAndGroups(PartslinkScraperService.ScrapedVehicleDetails details) {
        String vin = details.vin().trim().toUpperCase();
        Optional<PartslinkVehicle> existing = vehicleRepository.findByVin(vin);
        if (existing.isPresent()) {
            // Self-heal d'un cache incomplet/ancien (ex. lignes pré-migration V7 sans brand_code,
            // ou véhicule présent sans groupes) — on ne casse jamais une valeur existante.
            PartslinkVehicle v = existing.get();
            boolean changed = false;
            if (!org.springframework.util.StringUtils.hasText(v.getBrandCode())
                    && org.springframework.util.StringUtils.hasText(details.brandCode())) {
                v.setBrandCode(details.brandCode());
                changed = true;
            }
            if (changed) {
                vehicleRepository.save(v);
                log.info("[Cache] VIN={} backfill brand_code (était vide).", vin);
            }
            List<PartslinkGroup> existingGroups = groupRepository.findByVehicle(v);
            if (existingGroups.isEmpty() && details.groups() != null && !details.groups().isEmpty()) {
                for (PartslinkScraperService.ScrapedGroup g : details.groups()) {
                    groupRepository.save(PartslinkGroup.builder().vehicle(v).code(g.code()).name(g.name()).build());
                }
                log.info("[Cache] VIN={} backfill {} groupes (véhicule sans groupes).", vin, details.groups().size());
            }
            log.info("[Cache] Vehicle VIN={} déjà en cache (self-heal vérifié).", vin);
            return;
        }

        PartslinkVehicle vehicle = PartslinkVehicle.builder()
                .vin(vin)
                .model(details.model())
                .modelDesignation(details.modelDesignation())
                .productionDate(details.productionDate())
                .color(details.color())
                .upholstery(details.upholstery())
                .transmission(details.transmission())
                .modelCode(details.modelCode())
                .brandCode(details.brandCode())
                .build();
        
        vehicle = vehicleRepository.save(vehicle);

        if (details.groups() != null) {
            for (PartslinkScraperService.ScrapedGroup groupInfo : details.groups()) {
                PartslinkGroup group = PartslinkGroup.builder()
                        .vehicle(vehicle)
                        .code(groupInfo.code())
                        .name(groupInfo.name())
                        .build();
                groupRepository.save(group);
            }
        }
        log.info("[Cache] Persisted vehicle and {} groups in database for VIN={} (brand={})", 
                details.groups() != null ? details.groups().size() : 0, vin, details.brandCode());
    }

    @Transactional
    public List<PartslinkScraperService.ScrapedSubgroup> getOrFetchSubgroups(String vin, String groupCode) {
        String cleanVin = vin.trim().toUpperCase();
        PartslinkVehicle vehicle = vehicleRepository.findByVin(cleanVin)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Véhicule VIN=" + cleanVin + " absent du cache. Recherchez d'abord le VIN."));

        String brandCode = vehicle.getBrandCode();
        PartslinkGroup group = groupRepository.findByVehicleAndCode(vehicle, groupCode).orElse(null);

        // Parent absent en cache → refresh ciblé des groupes pour ce VIN avant d'échouer.
        if (group == null) {
            log.warn("[Cache] VIN={} vehicleId={} groupCode={} : groupe parent ABSENT du cache → refresh ciblé.",
                    cleanVin, vehicle.getId(), groupCode);
            group = refreshGroupsAndReget(vehicle, cleanVin, brandCode, groupCode);
            if (group == null) {
                throw new PartslinkGroupNotFoundException(cleanVin, groupCode);
            }
        }

        List<PartslinkSubgroup> cachedSubgroups = subgroupRepository.findByGroup(group);
        if (!cachedSubgroups.isEmpty()) {
            log.info("[Cache] subgroups CACHE_HIT vin={} vehicleId={} groupCode={} count={} source=cache",
                    cleanVin, vehicle.getId(), groupCode, cachedSubgroups.size());
            return cachedSubgroups.stream()
                    .map(sg -> new PartslinkScraperService.ScrapedSubgroup(sg.getCode(), sg.getName()))
                    .toList();
        }

        log.info("[Cache] subgroups CACHE_MISS vin={} vehicleId={} groupCode={} brandKnown={} source=selenium",
                cleanVin, vehicle.getId(), groupCode, org.springframework.util.StringUtils.hasText(brandCode));

        List<PartslinkScraperService.ScrapedSubgroup> scrapedSubgroups =
                scrapeSubgroupsWithRefresh(cleanVin, groupCode, brandCode);

        for (PartslinkScraperService.ScrapedSubgroup sgInfo : scrapedSubgroups) {
            subgroupRepository.save(PartslinkSubgroup.builder()
                    .group(group).code(sgInfo.code()).name(sgInfo.name()).build());
        }
        log.info("[Cache] subgroups SCRAPED vin={} groupCode={} count={}", cleanVin, groupCode, scrapedSubgroups.size());
        return scrapedSubgroups;
    }

    /**
     * Scrape les sous-groupes dans une session isolée.
     *
     * <p><b>Perf (preuve logs) :</b> l'ancien "refresh ciblé" ré-identifiait le VIN complet puis
     * retentait — or quand la 1ʳᵉ tentative revient <b>vide sans exception</b>, c'est un échec
     * <b>structurel</b> (page véhicule chargée mais 0 ligne sous-groupe pour cette marque) : la
     * ré-identification répète exactement le même rows=0 et <b>double le délai</b> (12→30s). On
     * échoue donc <b>vite</b> sur vide structurel, et on ne retente (1 fois, léger) que sur erreur
     * <b>transitoire</b> (élément périmé/navigation), sans ré-identification complète.</p>
     */
    private List<PartslinkScraperService.ScrapedSubgroup> scrapeSubgroupsWithRefresh(
            String vin, String groupCode, String brandCode) {
        return sessionPool.withLeasedDriver(driver -> {
            try {
                List<PartslinkScraperService.ScrapedSubgroup> r =
                        scraperService.fetchSubgroups(driver, vin, groupCode, brandCode);
                if (r != null && !r.isEmpty()) {
                    return r;
                }
                // Vide SANS exception = structurel → pas de refresh inutile, échec rapide.
                log.warn("[Cache] subgroups rows=0 STRUCTUREL vin={} groupCode={} → échec rapide (pas de re-identify)",
                        vin, groupCode);
                throw new PartslinkGroupNotFoundException(vin, groupCode);
            } catch (PartslinkGroupNotFoundException notFound) {
                throw notFound;
            } catch (RuntimeException transient_) {
                // Erreur transitoire → 1 seul retry léger (même page, pas de ré-identification VIN).
                log.warn("[Cache] subgroups erreur transitoire vin={} groupCode={} : {} → 1 retry",
                        vin, groupCode, transient_.getMessage());
                try {
                    List<PartslinkScraperService.ScrapedSubgroup> retry =
                            scraperService.fetchSubgroups(driver, vin, groupCode, brandCode);
                    if (retry != null && !retry.isEmpty()) {
                        return retry;
                    }
                } catch (RuntimeException ignored) {
                    // tombe dans l'erreur métier propre ci-dessous
                }
                throw new PartslinkGroupNotFoundException(vin, groupCode);
            }
        });
    }

    /** Ré-identifie le VIN (groupes) si la marque est connue, persiste, et retourne le groupe demandé. */
    private PartslinkGroup refreshGroupsAndReget(PartslinkVehicle vehicle, String vin, String brandCode, String groupCode) {
        if (!org.springframework.util.StringUtils.hasText(brandCode)) {
            log.warn("[Cache] refresh groupes impossible vin={} : brand_code inconnu (re-recherche VIN requise).", vin);
            return null;
        }
        PartslinkScraperService.ScrapedVehicleDetails details = sessionPool.withLeasedDriver(driver ->
                scraperService.identifyVehicleAndGroups(driver, vin, brandCode, s -> { }));
        saveVehicleAndGroups(details);
        return groupRepository.findByVehicleAndCode(vehicle, groupCode).orElse(null);
    }

    /** Backfill non destructif du brand_code (déclenché par la recherche VIN sur cache hit). */
    @Transactional
    public void backfillBrandCode(String vin, String brand) {
        if (!org.springframework.util.StringUtils.hasText(brand)) {
            return;
        }
        String cleanVin = vin.trim().toUpperCase();
        vehicleRepository.findByVin(cleanVin).ifPresent(v -> {
            if (!org.springframework.util.StringUtils.hasText(v.getBrandCode())) {
                v.setBrandCode(brand.trim());
                vehicleRepository.save(v);
                log.info("[Cache] VIN={} backfill brand_code via recherche VIN.", cleanVin);
            }
        });
    }

    /** Dump non sensible de la structure cache d'un VIN (endpoint admin de debug). */
    @Transactional(readOnly = true)
    public java.util.Map<String, Object> dumpStructure(String vin) {
        String cleanVin = vin.trim().toUpperCase();
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("vin", cleanVin);
        Optional<PartslinkVehicle> vOpt = vehicleRepository.findByVin(cleanVin);
        if (vOpt.isEmpty()) {
            out.put("cached", false);
            return out;
        }
        PartslinkVehicle v = vOpt.get();
        out.put("cached", true);
        out.put("vehicleId", v.getId());
        out.put("brandCode", v.getBrandCode());
        out.put("model", v.getModel());
        List<PartslinkGroup> groups = groupRepository.findByVehicle(v);
        List<java.util.Map<String, Object>> groupDump = new ArrayList<>();
        for (PartslinkGroup g : groups) {
            java.util.Map<String, Object> gm = new java.util.LinkedHashMap<>();
            gm.put("groupId", g.getId());
            gm.put("code", g.getCode());
            gm.put("name", g.getName());
            gm.put("subgroupCount", subgroupRepository.findByGroup(g).size());
            groupDump.add(gm);
        }
        out.put("groupCount", groups.size());
        out.put("groups", groupDump);
        return out;
    }

    @Transactional
    public PartslinkScraperService.ScrapedSubgroupDetails getOrFetchSubgroupDetails(String vin, String subgroupCode) {
        String cleanVin = vin.trim().toUpperCase();
        PartslinkVehicle vehicle = vehicleRepository.findByVin(cleanVin)
                .orElseThrow(() -> new IllegalArgumentException("Véhicule VIN=" + cleanVin + " absent du cache."));
        String brandCode = vehicle.getBrandCode();

        // Localiser le sous-groupe ET son groupe parent (nécessaire pour re-naviguer vers le schéma).
        List<PartslinkGroup> groups = groupRepository.findByVehicle(vehicle);
        PartslinkSubgroup subgroup = null;
        PartslinkGroup parentGroup = null;
        for (PartslinkGroup group : groups) {
            Optional<PartslinkSubgroup> sgOpt = subgroupRepository.findByGroupAndCode(group, subgroupCode);
            if (sgOpt.isPresent()) {
                subgroup = sgOpt.get();
                parentGroup = group;
                break;
            }
        }
        String parentGroupCode = parentGroup != null ? parentGroup.getCode() : null;

        if (subgroup == null) {
            // Sous-groupe pas (encore) en cache : scrape direct best-effort (parent inconnu).
            log.info("[Cache] details sous-groupe ABSENT du cache code={} vin={} → scrape direct", subgroupCode, cleanVin);
            return scrapeDetailsWithRefresh(cleanVin, brandCode, null, subgroupCode);
        }

        Optional<PartslinkSchematic> cachedSchematic = schematicRepository.findBySubgroup(subgroup);
        List<PartslinkPart> cachedParts = partRepository.findBySubgroup(subgroup);

        if (cachedSchematic.isPresent() && !cachedParts.isEmpty()) {
            log.info("[Cache] details CACHE_HIT vin={} subgroup={} parts={} source=cache",
                    cleanVin, subgroupCode, cachedParts.size());
            List<PartslinkScraperService.ScrapedPart> scrapedParts = cachedParts.stream()
                    .map(p -> new PartslinkScraperService.ScrapedPart(
                            p.getPosition(),
                            p.getPartNumber(),
                            p.getDesignation(),
                            p.getInfoSuppl(),
                            p.getQuantity(),
                            p.getAe()
                    ))
                    .toList();
            return new PartslinkScraperService.ScrapedSubgroupDetails(cachedSchematic.get().getImagePath(), scrapedParts);
        }

        log.info("[Cache] details CACHE_MISS vin={} subgroup={} group={} brandKnown={} source=selenium",
                cleanVin, subgroupCode, parentGroupCode,
                org.springframework.util.StringUtils.hasText(brandCode));
        PartslinkScraperService.ScrapedSubgroupDetails details =
                scrapeDetailsWithRefresh(cleanVin, brandCode, parentGroupCode, subgroupCode);

        // Save parts
        if (details.parts() != null) {
            for (PartslinkScraperService.ScrapedPart partInfo : details.parts()) {
                PartslinkPart part = PartslinkPart.builder()
                        .subgroup(subgroup)
                        .position(partInfo.position())
                        .partNumber(partInfo.partNumber())
                        .designation(partInfo.designation())
                        .infoSuppl(partInfo.infoSuppl())
                        .quantity(partInfo.quantity())
                        .ae(partInfo.ae())
                        .build();
                partRepository.save(part);
            }
        }

        // Save schematic
        if (details.imagePath() != null) {
            PartslinkSchematic schematic = PartslinkSchematic.builder()
                    .subgroup(subgroup)
                    .imagePath(details.imagePath())
                    .build();
            schematicRepository.save(schematic);
        }

        log.info("[Cache] details SCRAPED vin={} subgroup={} parts={} schematic={}",
                cleanVin, subgroupCode, details.parts() == null ? 0 : details.parts().size(),
                details.imagePath() != null);
        return details;
    }

    /**
     * Scrape les détails (schéma + pièces) dans une session isolée, avec un retry ciblé.
     * Le scraper re-navigue lui-même véhicule → groupe → sous-groupe (autonome). Sans marque
     * NI groupe parent connus, la re-navigation n'est pas fiable → erreur métier propre (409).
     */
    private PartslinkScraperService.ScrapedSubgroupDetails scrapeDetailsWithRefresh(
            String vin, String brandCode, String groupCode, String subgroupCode) {
        return sessionPool.withLeasedDriver(driver -> {
            try {
                return scraperService.fetchPartsAndSchematic(driver, vin, brandCode, groupCode, subgroupCode);
            } catch (RuntimeException ex) {
                log.warn("[Cache] 1er essai détails en échec vin={} subgroup={} : {}", vin, subgroupCode, ex.getMessage());
                boolean canRetry = org.springframework.util.StringUtils.hasText(brandCode)
                        && org.springframework.util.StringUtils.hasText(groupCode);
                if (!canRetry) {
                    throw new PartslinkGroupNotFoundException(vin, subgroupCode);
                }
                try {
                    return scraperService.fetchPartsAndSchematic(driver, vin, brandCode, groupCode, subgroupCode);
                } catch (RuntimeException ex2) {
                    log.warn("[Cache] retry détails en échec vin={} subgroup={} : {}", vin, subgroupCode, ex2.getMessage());
                    throw new PartslinkGroupNotFoundException(vin, subgroupCode);
                }
            }
        });
    }
}
