package com.reapro.achat.Controller;

import com.reapro.achat.DTO.*;
import com.reapro.achat.services.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Administration RBAC (Lot 2) : gestion des utilisateurs et attribution des permissions.
 *
 * <p>L'autorisation fine (super-admin / USER_MANAGEMENT_ACCESS / PERMISSION_ASSIGNMENT_ACCESS) et
 * l'anti-escalade sont appliquées dans {@link UserAdminService} (le backend reste la source de vérité).
 * Au niveau HTTP, ces routes exigent simplement une session authentifiée
 * (cf. SecurityConfig {@code anyRequest().authenticated()}). Le principal = email (posé par JwtFilter).</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserAdminService userAdminService;

    // ── Lectures ──────────────────────────────────────────────────────────────

    @GetMapping("/users")
    public Page<RbacUserSummary> listUsers(@AuthenticationPrincipal String email,
                                           Pageable pageable,
                                           @RequestParam(required = false) String search) {
        return userAdminService.listUsers(email, pageable, search);
    }

    @GetMapping("/users/{id}")
    public RbacUserDetail getUser(@AuthenticationPrincipal String email, @PathVariable Long id) {
        return userAdminService.getUser(email, id);
    }

    @GetMapping("/permissions")
    public List<RbacPermissionResponse> listPermissions(@AuthenticationPrincipal String email) {
        return userAdminService.listPermissions(email);
    }

    @GetMapping("/users/{id}/permissions")
    public List<String> getUserPermissions(@AuthenticationPrincipal String email, @PathVariable Long id) {
        return userAdminService.getUserPermissions(email, id);
    }

    // ── Gestion utilisateurs (super-admin / USER_MANAGEMENT_ACCESS) ────────────

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public RbacUserSummary createUser(@AuthenticationPrincipal String email,
                                      @RequestBody CreateUserRequest req) {
        return userAdminService.createUser(email, req);
    }

    @PatchMapping("/users/{id}/active")
    public MessageResponse setActive(@AuthenticationPrincipal String email,
                                     @PathVariable Long id,
                                     @RequestBody SetActiveRequest req) {
        if (req == null || req.active() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le champ 'active' est obligatoire.");
        }
        return userAdminService.setActive(email, id, req.active());
    }

    @PostMapping("/users/{id}/reset-password")
    public MessageResponse resetPassword(@AuthenticationPrincipal String email, @PathVariable Long id) {
        return userAdminService.resetPassword(email, id);
    }

    @PutMapping("/users/{id}")
    public MessageResponse updateUser(@AuthenticationPrincipal String email,
                                      @PathVariable Long id,
                                      @RequestBody UpdateUserInfoRequest req) {
        return userAdminService.updateUserInfo(email, id, req);
    }

    // ── Attribution des permissions (super-admin / PERMISSION_ASSIGNMENT_ACCESS) ─

    @PostMapping("/users/{id}/permissions")
    public RbacUserDetail assignPermissions(@AuthenticationPrincipal String email,
                                            @PathVariable Long id,
                                            @RequestBody PermissionCodesRequest req) {
        return userAdminService.assignPermissions(email, id, req != null ? req.codes() : null);
    }

    @PostMapping("/users/{id}/permissions/revoke")
    public RbacUserDetail revokePermissions(@AuthenticationPrincipal String email,
                                            @PathVariable Long id,
                                            @RequestBody PermissionCodesRequest req) {
        return userAdminService.revokePermissions(email, id, req != null ? req.codes() : null);
    }
}
