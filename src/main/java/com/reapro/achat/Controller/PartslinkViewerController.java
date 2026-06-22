package com.reapro.achat.Controller;

import com.reapro.achat.partslink.PartslinkSessionResponse;
import com.reapro.achat.partslink.PartslinkSessionService;
import com.reapro.achat.partslink.PartslinkSessionStatusResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Gestion "session" Partslink côté API. Refonte :
 * <ul>
 *   <li>plus de viewer iframe (endpoint legacy {@code /partslink-viewer/{id}} supprimé —
 *       on n'expose jamais Chrome/Selenium directement à l'utilisateur) ;</li>
 *   <li>{@code /session} et {@code /session/status} reflètent désormais le POOL (non destructifs) ;</li>
 *   <li>{@code /session/restart} et {@code /session/close} agissent sur TOUT le pool → réservés
 *       aux administrateurs ({@code ROLE_SUPERADMIN}). Un utilisateur standard ne peut plus couper
 *       les sessions de tous les autres.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/partslink")
@RequiredArgsConstructor
public class PartslinkViewerController {

    private final PartslinkSessionService partslinkSessionService;

    @PreAuthorize("hasAuthority('PARTSLINK_ACCESS')")
    @PostMapping("/session")
    public ResponseEntity<?> createOrGetSession() {
        try {
            PartslinkSessionResponse response = partslinkSessionService.createOrGetSession();
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
        }
    }

    @PreAuthorize("hasAuthority('PARTSLINK_ACCESS')")
    @GetMapping("/session/status")
    public ResponseEntity<PartslinkSessionStatusResponse> getSessionStatus() {
        return ResponseEntity.ok(partslinkSessionService.getSessionStatus());
    }

    /** ADMIN uniquement : recycle tout le pool (maintenance). */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/session/restart")
    public ResponseEntity<?> restartSession() {
        try {
            PartslinkSessionResponse response = partslinkSessionService.restartSession();
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
        }
    }

    /** ADMIN uniquement : ferme tout le pool (maintenance). */
    @PreAuthorize("hasRole('SUPERADMIN')")
    @PostMapping("/session/close")
    public ResponseEntity<?> closeSession() {
        try {
            partslinkSessionService.closeSession();
            return ResponseEntity.ok(Map.of("message", "Partslink pool closed"));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
        }
    }
}
