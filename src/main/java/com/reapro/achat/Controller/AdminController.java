package com.reapro.achat.Controller;

import com.reapro.achat.DTO.*;
import com.reapro.achat.services.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admins")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

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

    @PutMapping("/me/company")
    public AdminProfileResponse updateMyCompany(
            @AuthenticationPrincipal String email,
            @RequestBody UpdateMyCompanyRequest request
    ) {
        return adminService.updateMyCompany(email, request);
    }
}