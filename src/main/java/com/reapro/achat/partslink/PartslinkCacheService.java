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
            log.info("[Cache] Vehicle VIN={} already cached.", vin);
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
                .orElseThrow(() -> new IllegalArgumentException("Vehicule avec le VIN " + cleanVin + " non trouve dans le cache. Recherchez d'abord le VIN."));

        PartslinkGroup group = groupRepository.findByVehicleAndCode(vehicle, groupCode)
                .orElseThrow(() -> new IllegalArgumentException("Groupe '" + groupCode + "' non trouve pour le vehicule VIN=" + cleanVin));

        List<PartslinkSubgroup> cachedSubgroups = subgroupRepository.findByGroup(group);
        if (!cachedSubgroups.isEmpty()) {
            log.info("[Cache] Subgroups cache hit for VIN={} Group={}", cleanVin, groupCode);
            return cachedSubgroups.stream()
                    .map(sg -> new PartslinkScraperService.ScrapedSubgroup(sg.getCode(), sg.getName()))
                    .toList();
        }

        log.info("[Cache] Subgroups cache miss for VIN={} Group={}. Réservation d'un slot du pool...", cleanVin, groupCode);
        // Pass brand code to the scraper so it can navigate back to the vehicle page if needed
        String brandCode = vehicle.getBrandCode();
        List<PartslinkScraperService.ScrapedSubgroup> scrapedSubgroups =
                sessionPool.withLeasedDriver(driver -> scraperService.fetchSubgroups(driver, cleanVin, groupCode, brandCode));

        for (PartslinkScraperService.ScrapedSubgroup sgInfo : scrapedSubgroups) {
            PartslinkSubgroup subgroup = PartslinkSubgroup.builder()
                    .group(group)
                    .code(sgInfo.code())
                    .name(sgInfo.name())
                    .build();
            subgroupRepository.save(subgroup);
        }

        return scrapedSubgroups;
    }

    @Transactional
    public PartslinkScraperService.ScrapedSubgroupDetails getOrFetchSubgroupDetails(String vin, String subgroupCode) {
        String cleanVin = vin.trim().toUpperCase();
        PartslinkVehicle vehicle = vehicleRepository.findByVin(cleanVin)
                .orElseThrow(() -> new IllegalArgumentException("Vehicule avec le VIN " + cleanVin + " non trouve."));

        // Since we don't have the parent groupCode in this path, we can find the subgroup by traversing through the vehicle's groups.
        List<PartslinkGroup> groups = groupRepository.findByVehicle(vehicle);
        PartslinkSubgroup subgroup = null;
        for (PartslinkGroup group : groups) {
            Optional<PartslinkSubgroup> sgOpt = subgroupRepository.findByGroupAndCode(group, subgroupCode);
            if (sgOpt.isPresent()) {
                subgroup = sgOpt.get();
                break;
            }
        }

        if (subgroup == null) {
            log.info("[Cache] Subgroup entity not found in db for code={}. Réservation d'un slot du pool...", subgroupCode);
            return sessionPool.withLeasedDriver(driver -> scraperService.fetchPartsAndSchematic(driver, cleanVin, subgroupCode));
        }

        Optional<PartslinkSchematic> cachedSchematic = schematicRepository.findBySubgroup(subgroup);
        List<PartslinkPart> cachedParts = partRepository.findBySubgroup(subgroup);

        if (cachedSchematic.isPresent() && !cachedParts.isEmpty()) {
            log.info("[Cache] Subgroup details cache hit for VIN={} Subgroup={}", cleanVin, subgroupCode);
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

        log.info("[Cache] Subgroup details cache miss for VIN={} Subgroup={}. Réservation d'un slot du pool...", cleanVin, subgroupCode);
        PartslinkScraperService.ScrapedSubgroupDetails details =
                sessionPool.withLeasedDriver(driver -> scraperService.fetchPartsAndSchematic(driver, cleanVin, subgroupCode));

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

        return details;
    }
}
