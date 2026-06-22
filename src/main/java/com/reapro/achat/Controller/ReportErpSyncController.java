package com.reapro.achat.Controller;

import com.reapro.achat.services.ReportErpSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/sync-adaptable")
public class ReportErpSyncController {

    private final ReportErpSyncService reportErpSyncService;

    public ReportErpSyncController(ReportErpSyncService reportErpSyncService) {
        this.reportErpSyncService = reportErpSyncService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADAPTABLE_SYNC_ACCESS')")
    public Mono<ResponseEntity<String>> getSyncData(
            @AuthenticationPrincipal String email,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String tdBrandName,
            @RequestParam(required = false) String partGroup,
            @RequestParam(required = false) String partSubGroup,
            @RequestParam(required = false) String master) {
        
        return reportErpSyncService.getLocalSyncData(page, pageSize, tdBrandName, partGroup, partSubGroup, master, email)
                .map(data -> ResponseEntity.ok().contentType(org.springframework.http.MediaType.APPLICATION_JSON).body(data))
                .onErrorResume(e -> Mono.just(ResponseEntity.status(500).body("Error retrieving sync data: " + e.getMessage())))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('ADAPTABLE_SYNC_RUN')")
    public ResponseEntity<Map<String, String>> triggerSync() {
        if (reportErpSyncService.isSyncing()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "La synchronisation est déjà en cours."));
        }
        
        reportErpSyncService.startSync();
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("message", "Synchronisation démarrée en arrière-plan."));
    }

    @GetMapping("/sync-status")
    @PreAuthorize("hasAuthority('ADAPTABLE_SYNC_ACCESS')")
    public ResponseEntity<Map<String, Boolean>> getSyncStatus() {
        return ResponseEntity.ok(Map.of("isSyncing", reportErpSyncService.isSyncing()));
    }
}
