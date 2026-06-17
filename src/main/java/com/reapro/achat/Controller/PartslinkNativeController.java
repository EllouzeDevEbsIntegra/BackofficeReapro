package com.reapro.achat.Controller;

import com.reapro.achat.partslink.PartslinkCacheService;
import com.reapro.achat.partslink.PartslinkJobAccessException;
import com.reapro.achat.partslink.PartslinkPoolBusyException;
import com.reapro.achat.partslink.PartslinkScraperService;
import com.reapro.achat.partslink.PartslinkSearchJobManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/partslink")
@RequiredArgsConstructor
@Slf4j
public class PartslinkNativeController {

    private final PartslinkSearchJobManager jobManager;
    private final PartslinkCacheService cacheService;

    @PostMapping("/search/vin")
    public ResponseEntity<?> searchByVin(
            @RequestParam("vin") String vin,
            @RequestParam(value = "brand", required = false) String brand,
            Principal principal
    ) {
        String userId = requireUser(principal);
        String normalizedVin = vin == null ? "" : vin.trim().toUpperCase();
        if (normalizedVin.length() != 17) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Le VIN doit contenir 17 caracteres."));
        }

        // Cache-first : un hit ne réserve AUCUNE session navigateur (réponse instantanée).
        Optional<com.reapro.achat.entities.primary.PartslinkVehicle> vehicleOpt = cacheService.findVehicleByVin(normalizedVin);
        if (vehicleOpt.isPresent()) {
            com.reapro.achat.entities.primary.PartslinkVehicle vehicleEntity = vehicleOpt.get();
            List<com.reapro.achat.entities.primary.PartslinkGroup> groupsList = cacheService.findGroupsByVehicle(vehicleEntity);

            log.info("[Controller] Cache hit for VIN={} (réponse instantanée, owner={})", normalizedVin, userId);
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("jobId", "CACHE-" + normalizedVin);
            response.put("vin", normalizedVin);
            response.put("brand", brand);
            response.put("status", "COMPLETED");
            response.put("step", "Charge depuis le cache local (Instant).");
            response.put("completed", true);
            response.put("error", null);
            response.put("vehicle", vehicleToMap(vehicleEntity));
            response.put("groups", groupsToMap(groupsList));
            response.put("createdAt", System.currentTimeMillis());
            return ResponseEntity.ok(response);
        }

        // Cache miss : soumettre un job scopé à l'utilisateur courant.
        String jobId = jobManager.submitVinSearch(normalizedVin, StringUtils.hasText(brand) ? brand.trim() : null, userId);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "QUEUED");
        response.put("jobId", jobId);
        response.put("completed", false);
        response.put("step", "En file d'attente...");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search/status/{jobId}")
    public ResponseEntity<?> getSearchStatus(@PathVariable("jobId") String jobId, Principal principal) {
        String userId = requireUser(principal);

        // Cache hit "virtuel" (jobId = CACHE-<vin>) : pas de job, pas d'isolation requise (donnée catalogue partagée).
        if (jobId != null && jobId.startsWith("CACHE-")) {
            String vin = jobId.substring("CACHE-".length());
            Optional<com.reapro.achat.entities.primary.PartslinkVehicle> vehicleOpt = cacheService.findVehicleByVin(vin);
            if (vehicleOpt.isPresent()) {
                com.reapro.achat.entities.primary.PartslinkVehicle vehicleEntity = vehicleOpt.get();
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("jobId", jobId);
                response.put("vin", vin);
                response.put("status", "COMPLETED");
                response.put("step", "Charge depuis le cache local (Instant).");
                response.put("completed", true);
                response.put("error", null);
                response.put("vehicle", vehicleToMap(vehicleEntity));
                response.put("groups", groupsToMap(cacheService.findGroupsByVehicle(vehicleEntity)));
                response.put("createdAt", System.currentTimeMillis());
                return ResponseEntity.ok(response);
            }
        }

        // Isolation stricte : un user ne peut jamais lire le job d'un autre (404 si non-propriétaire).
        Optional<PartslinkSearchJobManager.JobProgress> opt = jobManager.getJobForOwner(jobId, userId);
        if (opt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Job introuvable: " + jobId));
        }
        PartslinkSearchJobManager.JobProgress progress = opt.get();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobId", progress.getJobId());
        response.put("vin", progress.getVin());
        response.put("brand", progress.getBrand());
        response.put("status", progress.getStatus());
        response.put("step", progress.getStep());
        response.put("completed", progress.isCompleted());
        response.put("error", progress.getError());
        response.put("queuePosition", jobManager.queuePosition(progress));
        response.put("createdAt", progress.getCreatedAt());
        response.put("startedAt", progress.getStartedAt());
        response.put("finishedAt", progress.getFinishedAt());

        PartslinkScraperService.ScrapedVehicleDetails details = progress.getVehicleDetails();
        if (details != null) {
            Map<String, Object> vehicle = new LinkedHashMap<>();
            vehicle.put("vin", details.vin());
            vehicle.put("model", details.model());
            vehicle.put("modelDesignation", details.modelDesignation());
            vehicle.put("productionDate", details.productionDate());
            vehicle.put("color", details.color());
            vehicle.put("upholstery", details.upholstery());
            vehicle.put("transmission", details.transmission());
            vehicle.put("modelCode", details.modelCode());

            List<Map<String, Object>> groups = details.groups() == null ? List.of() : details.groups().stream()
                    .map(g -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", g.code());
                        item.put("code", g.code());
                        item.put("name", g.name());
                        return item;
                    })
                    .toList();
            response.put("vehicle", vehicle);
            response.put("groups", groups);
        } else {
            response.put("vehicle", null);
            response.put("groups", null);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/search/status/{jobId}/cancel")
    public ResponseEntity<?> cancelSearch(@PathVariable("jobId") String jobId, Principal principal) {
        String userId = requireUser(principal);
        boolean cancelled = jobManager.cancelJob(jobId, userId);
        if (!cancelled) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Job non annulable (introuvable, déjà démarré ou terminé)."));
        }
        return ResponseEntity.ok(Map.of("jobId", jobId, "status", "CANCELLED"));
    }

    @GetMapping("/vehicle/{vin}/groups/{groupId}/subgroups")
    public ResponseEntity<?> getSubgroups(
            @PathVariable("vin") String vin,
            @PathVariable("groupId") String groupId
    ) {
        try {
            List<PartslinkScraperService.ScrapedSubgroup> subgroups = cacheService.getOrFetchSubgroups(vin, groupId);
            List<Map<String, Object>> payload = subgroups.stream().map(sg -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", sg.code());
                row.put("code", sg.code());
                row.put("name", sg.name());
                return row;
            }).toList();
            return ResponseEntity.ok(payload);
        } catch (PartslinkPoolBusyException busy) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", busy.getMessage(), "busy", true));
        } catch (Exception ex) {
            log.error("[Controller] Failed to fetch subgroups: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Unknown error"));
        }
    }

    @GetMapping("/vehicle/{vin}/subgroups/{subgroupId}/details")
    public ResponseEntity<?> getSubgroupDetails(
            @PathVariable("vin") String vin,
            @PathVariable("subgroupId") String subgroupId
    ) {
        try {
            PartslinkScraperService.ScrapedSubgroupDetails details = cacheService.getOrFetchSubgroupDetails(vin, subgroupId);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("imagePath", details.imagePath());
            payload.put("parts", details.parts());
            return ResponseEntity.ok(payload);
        } catch (PartslinkPoolBusyException busy) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("message", busy.getMessage(), "busy", true));
        } catch (Exception ex) {
            log.error("[Controller] Failed to fetch subgroup details: {}", ex.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", ex.getMessage() != null ? ex.getMessage() : "Unknown error"));
        }
    }

    // --- helpers ---

    private String requireUser(Principal principal) {
        if (principal == null || !StringUtils.hasText(principal.getName())) {
            throw new PartslinkJobAccessException("Utilisateur non authentifié.");
        }
        return principal.getName();
    }

    private Map<String, Object> vehicleToMap(com.reapro.achat.entities.primary.PartslinkVehicle v) {
        Map<String, Object> vehicle = new LinkedHashMap<>();
        vehicle.put("vin", v.getVin());
        vehicle.put("model", v.getModel());
        vehicle.put("modelDesignation", v.getModelDesignation());
        vehicle.put("productionDate", v.getProductionDate());
        vehicle.put("color", v.getColor());
        vehicle.put("upholstery", v.getUpholstery());
        vehicle.put("transmission", v.getTransmission());
        vehicle.put("modelCode", v.getModelCode());
        return vehicle;
    }

    private List<Map<String, Object>> groupsToMap(List<com.reapro.achat.entities.primary.PartslinkGroup> groupsList) {
        return groupsList.stream().map(g -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", g.getCode());
            item.put("code", g.getCode());
            item.put("name", g.getName());
            return item;
        }).toList();
    }
}
