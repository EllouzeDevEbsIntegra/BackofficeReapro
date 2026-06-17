package com.reapro.achat.Controller;

import com.reapro.achat.partslink.PartslinkCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Outil de debug Partslink, réservé aux administrateurs ({@code ROLE_SUPERADMIN}).
 * Dumpe la structure CACHE d'un VIN (véhicule → groupes → nb sous-groupes) pour
 * diagnostiquer un cache incohérent ("groupe introuvable") — sans aucune donnée sensible.
 * À verrouiller/retirer après diagnostic.
 */
@RestController
@RequestMapping("/api/partslink/admin")
@RequiredArgsConstructor
@Slf4j
public class PartslinkAdminController {

    private final PartslinkCacheService cacheService;

    // ADMIN (et non SUPERADMIN) : lecture seule, données non sensibles (codes/compteurs),
    // afin que l'exploitant puisse diagnostiquer sur PROD. À retirer/verrouiller après diagnostic.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/debug/vin/{vin}")
    public ResponseEntity<Map<String, Object>> debugVin(@PathVariable("vin") String vin) {
        log.info("[Admin] debug dump cache structure pour VIN={}", vin == null ? "" : vin.trim().toUpperCase());
        return ResponseEntity.ok(cacheService.dumpStructure(vin));
    }
}
