package com.reapro.achat.Controller;

import com.reapro.achat.services.ElvaItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints d'administration pour les operations de maintenance PROD.
 *
 *  POST /api/admin/resync-items
 *      -> Vide la table elva_item_cache et re-synchronise tous les articles
 *        depuis SQL Server (ELVA_Item). La tache planifiee tourne deja
 *        toutes les heures, mais cet endpoint permet un declenchement manuel.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
public class SalesOrderSyncController {

    private final ElvaItemService elvaItemService;

    /**
     * Vide la table elva_item_cache et re-synchronise depuis SQL Server.
     * Equivalent a la tache CRON horaire mais declenchable manuellement.
     *
     * Exemple : POST /api/admin/resync-items
     */
    // RBAC Lot 4 : maintenance systeme (resync cache) -> SYSTEM_SETTINGS_ACCESS (super-admin bypass).
    @PostMapping("/resync-items")
    @PreAuthorize("hasAuthority('SYSTEM_SETTINGS_ACCESS')")
    public ResponseEntity<Map<String, Object>> resyncItems() {
        log.info("Declenchement manuel de la re-synchronisation des articles elva_item_cache");
        long start = System.currentTimeMillis();
        try {
            elvaItemService.syncElvaItemsFromSqlServer();
            long durationMs = System.currentTimeMillis() - start;
            log.info("Re-synchronisation des articles terminee en {}ms", durationMs);
            return ResponseEntity.ok(Map.of(
                    "status",    "success",
                    "message",   "Re-synchronisation des articles terminee",
                    "durationMs", durationMs
            ));
        } catch (Exception e) {
            log.error("Erreur lors de la re-synchronisation des articles", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status",  "error",
                    "message", e.getMessage()
            ));
        }
    }
}
