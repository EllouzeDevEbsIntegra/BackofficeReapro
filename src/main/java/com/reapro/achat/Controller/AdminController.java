package com.reapro.achat.Controller;

import com.reapro.achat.DTO.*;
import com.reapro.achat.services.AdminService;
import com.reapro.achat.services.ParameterService; // Import
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/admins")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ParameterService parameterService; // Import

    // ... (toutes les autres méthodes restent identiques)

    // ──────────────────────────────────────────────────────────────
    // Routes accessibles à TOUS les utilisateurs connectés (ADMIN + USER)
    // ──────────────────────────────────────────────────────────────

    @GetMapping("/me")
    public AdminProfileResponse getMyProfile(@AuthenticationPrincipal String email) {
        return adminService.getMyProfile(email);
    }

    @PutMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    public String updateMyProfile(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody UpdateProfileRequest request) {
        return adminService.updateMyProfile(email, request);
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.OK)
    public String changeMyPassword(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody ChangeMyPasswordRequest request) {
        return adminService.changeMyPassword(email, request);
    }

    // ──────────────────────────────────────────────────────────────
    // Routes réservées UNIQUEMENT aux ROLE_ADMIN
    // ──────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping
    public Page<AdminListResponse> getAllAdmins(
            Pageable pageable,
            @RequestParam(required = false) String search) {
        return adminService.getAllAdmins(pageable, search);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @GetMapping("/{id}")
    public AdminDetailResponse getAdminById(@PathVariable Long id) {
        return adminService.getAdminById(id);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public String updateAdmin(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAdminRequest request) {
        return adminService.updateAdmin(id, request);
    }

    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PatchMapping("/{id}/toggle-active")
    @ResponseStatus(HttpStatus.OK)
    public String toggleAdminActive(@PathVariable Long id) {
        return adminService.toggleAdminActive(id);
    }

    // ⛔ RBAC : la société d'un utilisateur n'est PLUS modifiable par l'utilisateur lui-même.
    // L'affectation société est désormais réservée au SUPER ADMIN via
    // PATCH /api/admin/users/{id}/company (UserAdminController). La société reste visible
    // en lecture seule dans le profil (GET /api/admins/me).

    // ✅ NOUVEL ENDPOINT TEMPORAIRE POUR METTRE À JOUR UN PARAMÈTRE
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/update-parameter")
    @ResponseStatus(HttpStatus.OK)
    public String updateParameter(@RequestBody Map<String, String> payload) {
        String key = payload.get("key");
        String value = payload.get("value");
        if (key == null || value == null) {
            return "Veuillez fournir 'key' et 'value' dans le corps de la requête.";
        }
        parameterService.updateValue(key, value);
        return "Paramètre '" + key + "' mis à jour avec la valeur '" + value + "'.";
    }
}
