package com.reapro.achat.Controller;

import com.reapro.achat.partslink.PartslinkCacheService;
import com.reapro.achat.partslink.PartslinkScraperService;
import com.reapro.achat.partslink.PartslinkSessionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final PartslinkSessionPool sessionPool;
    private final PartslinkScraperService scraperService;

    // ADMIN (et non SUPERADMIN) : lecture seule, données non sensibles (codes/compteurs),
    // afin que l'exploitant puisse diagnostiquer sur PROD. À retirer/verrouiller après diagnostic.
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/debug/vin/{vin}")
    public ResponseEntity<Map<String, Object>> debugVin(@PathVariable("vin") String vin) {
        log.info("[Admin] debug dump cache structure pour VIN={}", vin == null ? "" : vin.trim().toUpperCase());
        return ResponseEntity.ok(cacheService.dumpStructure(vin));
    }

    /**
     * Inspection LIVE (1 scrape contrôlé) : navigue + recherche VIN puis dumpe l'état DOM
     * post-recherche (URL, titre, présence groupes, texte court, pageState) — sans secret.
     * Permet de caractériser la page Mercedes "Page d'accueil"/sélection. À retirer après diagnostic.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/debug/live/{vin}")
    public ResponseEntity<Map<String, Object>> debugLive(
            @PathVariable("vin") String vin,
            @RequestParam(value = "brand", required = false) String brand) {
        String cleanVin = vin == null ? "" : vin.trim().toUpperCase();
        String cleanBrand = StringUtils.hasText(brand) ? brand.trim() : null;
        log.info("[Admin] debug LIVE post-search dump vin={} brand={}", cleanVin, cleanBrand);
        Map<String, Object> dump = sessionPool.withLeasedDriver(
                driver -> scraperService.inspectAfterVinSearch(driver, cleanVin, cleanBrand));
        return ResponseEntity.ok(dump);
    }

    /**
     * Inspection LIVE niveau SOUS-GROUPES : navigue véhicule → clique le groupe → dumpe l'état DOM
     * réel après clic (sélecteurs, compteurs multi-sélecteurs, 1ʳᵉ ligne, iframe/shadow, timings) —
     * sans secret. Révèle pourquoi Audi/Mercedes renvoient rows=0. À retirer après diagnostic.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/debug/subgroups/{vin}")
    public ResponseEntity<Map<String, Object>> debugSubgroups(
            @PathVariable("vin") String vin,
            @RequestParam(value = "brand", required = false) String brand,
            @RequestParam("groupCode") String groupCode) {
        String cleanVin = vin == null ? "" : vin.trim().toUpperCase();
        String cleanBrand = StringUtils.hasText(brand) ? brand.trim() : null;
        log.info("[Admin] debug SUBGROUPS dump vin={} brand={} groupCode={}", cleanVin, cleanBrand, groupCode);
        Map<String, Object> dump = sessionPool.withLeasedDriver(
                driver -> scraperService.inspectSubgroups(driver, cleanVin, cleanBrand, groupCode));
        return ResponseEntity.ok(dump);
    }
}
