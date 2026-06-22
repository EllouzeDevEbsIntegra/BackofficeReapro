package com.reapro.achat.services;

import com.reapro.achat.DTO.*;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.entities.primary.Permission;
import com.reapro.achat.entities.primary.UserPermission;
import com.reapro.achat.enums.PermissionType;
import com.reapro.achat.enums.Role;
import com.reapro.achat.repositories.primary.AdminRepository;
import com.reapro.achat.repositories.primary.PermissionRepository;
import com.reapro.achat.repositories.primary.UserPermissionRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.util.*;

/**
 * Gestion utilisateurs + attribution de permissions (RBAC Lot 2).
 *
 * <p><b>Le backend est la source de vérité.</b> Toute l'autorisation et l'anti-escalade sont appliquées
 * ici, au niveau service (pas seulement via {@code @PreAuthorize}), car les règles dépendent de la
 * sensibilité de la permission ciblée et de l'identité de l'acteur/cible.</p>
 *
 * <p>Règles (cf. Reapro-RBAC-permissions-roadmap.md §7) :</p>
 * <ul>
 *   <li>super-admin (transitoire = {@code ROLE_ADMIN}) : peut tout faire ;</li>
 *   <li>{@code USER_MANAGEMENT_ACCESS} : gestion des comptes (créer / (dés)activer / reset MdP / infos) ;</li>
 *   <li>{@code PERMISSION_ASSIGNMENT_ACCESS} : attribuer/retirer uniquement des permissions <b>non sensibles</b>,
 *       jamais sur un super-admin, jamais ses propres permissions critiques ;</li>
 *   <li>permissions sensibles/admin : réservées au super-admin.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class UserAdminService {

    private final AdminRepository adminRepository;
    private final PermissionRepository permissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final EmailService emailService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom secureRandom = new SecureRandom();

    // ============================ Acteur courant ============================

    /** Acteur authentifié + droits résolus (super-admin / permissions). */
    private record Actor(Admin admin, boolean superAdmin, Set<String> permissions) {
        boolean has(String code) {
            return superAdmin || permissions.contains(code);
        }
    }

    private Actor actor(String email) {
        Admin a = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session invalide."));
        if (!a.isActive()) {
            // Défense en profondeur : un acteur désactivé ne doit rien pouvoir faire.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Compte désactivé.");
        }
        boolean superAdmin = isSuper(a);
        Set<String> perms = superAdmin
                ? Collections.emptySet()
                : new HashSet<>(userPermissionRepository.findByUserId(a.getId()).stream()
                        .map(UserPermission::getPermissionId)
                        .map(this::codeOf)
                        .filter(Objects::nonNull)
                        .toList());
        return new Actor(a, superAdmin, perms);
    }

    private boolean isSuper(Admin a) {
        return a != null && a.getRole() == Role.ROLE_ADMIN;
    }

    private String codeOf(Long permissionId) {
        return permissionRepository.findById(permissionId).map(Permission::getCode).orElse(null);
    }

    /** Une permission est « sensible » si marquée {@code sensitive} OU de type {@code ADMIN}. */
    private boolean isSensitive(Permission p) {
        return p.isSensitive() || p.getType() == PermissionType.ADMIN;
    }

    // ============================ Gates d'accès ============================

    private void requireReadAccess(Actor actor) {
        if (actor.superAdmin()
                || actor.has("USER_MANAGEMENT_ACCESS")
                || actor.has("PERMISSION_ASSIGNMENT_ACCESS")) {
            return;
        }
        throw forbidden("Accès refusé : droits d'administration requis.");
    }

    private void requireUserManagement(Actor actor) {
        if (actor.superAdmin() || actor.has("USER_MANAGEMENT_ACCESS")) {
            return;
        }
        throw forbidden("Accès refusé : gestion des utilisateurs réservée.");
    }

    private void requirePermissionAssignment(Actor actor) {
        if (actor.superAdmin() || actor.has("PERMISSION_ASSIGNMENT_ACCESS")) {
            return;
        }
        throw forbidden("Accès refusé : gestion des autorisations réservée.");
    }

    private void guardTargetNotSuperAdmin(Actor actor, Admin target) {
        if (!actor.superAdmin() && isSuper(target)) {
            throw forbidden("Action non autorisée sur un super-administrateur.");
        }
    }

    private ResponseStatusException forbidden(String msg) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, msg);
    }

    private ResponseStatusException badRequest(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private Admin findUser(Long id) {
        return adminRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable."));
    }

    // ============================ Lectures ============================

    @Transactional(readOnly = true)
    public Page<RbacUserSummary> listUsers(String actorEmail, Pageable pageable, String search) {
        requireReadAccess(actor(actorEmail));

        Page<Admin> page = (search == null || search.trim().isEmpty())
                ? adminRepository.findAll(pageable)
                : adminRepository.findByEmailContainingIgnoreCaseOrFirstnameContainingIgnoreCaseOrLastnameContainingIgnoreCase(
                        search.trim(), search.trim(), search.trim(), pageable);

        return page.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public RbacUserDetail getUser(String actorEmail, Long id) {
        requireReadAccess(actor(actorEmail));
        Admin target = findUser(id);
        return toDetail(target);
    }

    @Transactional(readOnly = true)
    public List<RbacPermissionResponse> listPermissions(String actorEmail) {
        requireReadAccess(actor(actorEmail));
        return permissionRepository.findAll().stream()
                .sorted(Comparator
                        .comparing((Permission p) -> Optional.ofNullable(p.getGroupName()).orElse(""))
                        .thenComparing(Permission::getCode))
                .map(this::toPermissionResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getUserPermissions(String actorEmail, Long id) {
        requireReadAccess(actor(actorEmail));
        Admin target = findUser(id);
        return effectivePermissionCodes(target);
    }

    // ============================ Gestion utilisateurs ============================

    public RbacUserSummary createUser(String actorEmail, CreateUserRequest req) {
        Actor actor = actor(actorEmail);
        requireUserManagement(actor);

        if (req == null || isBlank(req.email()) || isBlank(req.password())) {
            throw badRequest("Email et mot de passe sont obligatoires.");
        }
        String email = req.email().trim().toLowerCase();
        if (adminRepository.existsByEmail(email)) {
            throw badRequest("Cet email est déjà utilisé.");
        }
        if (!isPasswordStrong(req.password())) {
            throw badRequest("Le mot de passe doit contenir 8-20 caractères, avec majuscule, minuscule, chiffre et symbole.");
        }

        Admin user = Admin.builder()
                .firstname(trimOrNull(req.firstname()))
                .lastname(trimOrNull(req.lastname()))
                .email(email)
                .password(passwordEncoder.encode(req.password()))
                .active(true)
                // Anti-escalade : la création via API ne produit JAMAIS un compte privilégié.
                .role(Role.ROLE_USER)
                .build();
        adminRepository.save(user);

        log.info("[RBAC] actor={} created user id={} email={}", actorEmail, user.getId(), email);
        return toSummary(user);
    }

    public MessageResponse setActive(String actorEmail, Long id, boolean active) {
        Actor actor = actor(actorEmail);
        requireUserManagement(actor);
        Admin target = findUser(id);
        guardTargetNotSuperAdmin(actor, target);

        // Anti-lock : on ne peut pas modifier son propre statut (risque de se bloquer soi-même).
        if (target.getId().equals(actor.admin().getId())) {
            throw badRequest("Vous ne pouvez pas modifier votre propre statut.");
        }

        target.setActive(active);
        adminRepository.save(target);
        log.info("[RBAC] actor={} set active={} on user id={}", actorEmail, active, id);
        return new MessageResponse(active ? "Utilisateur activé." : "Utilisateur désactivé.");
    }

    public MessageResponse resetPassword(String actorEmail, Long id) {
        Actor actor = actor(actorEmail);
        requireUserManagement(actor);
        Admin target = findUser(id);
        guardTargetNotSuperAdmin(actor, target);

        String tempPassword = generateTempPassword();
        target.setPassword(passwordEncoder.encode(tempPassword));
        target.setTempPasswordExpireAt(java.time.LocalDateTime.now().plusHours(1));
        adminRepository.save(target);

        try {
            emailService.sendTempPasswordHtml(target.getEmail(), target.getFirstname(), tempPassword);
        } catch (MessagingException e) {
            // Ne jamais logger le mot de passe temporaire.
            log.error("[RBAC] reset password: envoi email échoué pour user id={}", id);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Mot de passe réinitialisé mais l'email n'a pas pu être envoyé.");
        }

        log.info("[RBAC] actor={} reset password for user id={}", actorEmail, id);
        return new MessageResponse("Mot de passe temporaire envoyé à l'utilisateur.");
    }

    public MessageResponse updateUserInfo(String actorEmail, Long id, UpdateUserInfoRequest req) {
        Actor actor = actor(actorEmail);
        requireUserManagement(actor);
        Admin target = findUser(id);
        guardTargetNotSuperAdmin(actor, target);

        if (req.firstname() != null && !req.firstname().isBlank()) {
            target.setFirstname(req.firstname().trim());
        }
        if (req.lastname() != null && !req.lastname().isBlank()) {
            target.setLastname(req.lastname().trim());
        }
        if (req.email() != null && !req.email().isBlank()) {
            String newEmail = req.email().trim().toLowerCase();
            if (!newEmail.equals(target.getEmail()) && adminRepository.existsByEmail(newEmail)) {
                throw badRequest("Cet email est déjà utilisé.");
            }
            target.setEmail(newEmail);
        }

        adminRepository.save(target);
        log.info("[RBAC] actor={} updated info of user id={}", actorEmail, id);
        return new MessageResponse("Utilisateur mis à jour.");
    }

    // ============================ Attribution des permissions ============================

    public RbacUserDetail assignPermissions(String actorEmail, Long id, List<String> codes) {
        Actor actor = actor(actorEmail);
        requirePermissionAssignment(actor);
        Admin target = findUser(id);
        guardTargetNotSuperAdmin(actor, target);

        List<Permission> perms = resolveAndCheck(actor, codes);
        for (Permission p : perms) {
            if (!userPermissionRepository.existsByUserIdAndPermissionId(target.getId(), p.getId())) {
                userPermissionRepository.save(UserPermission.builder()
                        .userId(target.getId())
                        .permissionId(p.getId())
                        .grantedBy(actor.admin().getId())
                        .build());
            }
        }
        log.info("[RBAC] actor={} assigned {} to user id={}", actorEmail, codesOf(perms), id);
        return toDetail(target);
    }

    public RbacUserDetail revokePermissions(String actorEmail, Long id, List<String> codes) {
        Actor actor = actor(actorEmail);
        requirePermissionAssignment(actor);
        Admin target = findUser(id);
        guardTargetNotSuperAdmin(actor, target);

        List<Permission> perms = resolveAndCheck(actor, codes);
        for (Permission p : perms) {
            // Anti-lock : interdiction de retirer ses PROPRES permissions critiques (sensibles/admin).
            if (target.getId().equals(actor.admin().getId()) && isSensitive(p)) {
                throw forbidden("Vous ne pouvez pas retirer vos propres permissions critiques (" + p.getCode() + ").");
            }
            userPermissionRepository.findByUserIdAndPermissionId(target.getId(), p.getId())
                    .ifPresent(userPermissionRepository::delete);
        }
        log.info("[RBAC] actor={} revoked {} from user id={}", actorEmail, codesOf(perms), id);
        return toDetail(target);
    }

    /**
     * Résout les codes en permissions existantes et applique l'anti-escalade :
     * un acteur non super-admin ne peut toucher aucune permission sensible/admin.
     */
    private List<Permission> resolveAndCheck(Actor actor, List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            throw badRequest("Aucune permission fournie.");
        }
        List<Permission> resolved = new ArrayList<>();
        for (String code : codes) {
            if (isBlank(code)) {
                continue;
            }
            Permission p = permissionRepository.findByCode(code.trim())
                    .orElseThrow(() -> badRequest("Permission inconnue : " + code));
            if (!actor.superAdmin() && isSensitive(p)) {
                throw forbidden("Permission sensible non attribuable par un gestionnaire d'autorisations : " + p.getCode());
            }
            resolved.add(p);
        }
        if (resolved.isEmpty()) {
            throw badRequest("Aucune permission valide fournie.");
        }
        return resolved;
    }

    // ============================ Mapping / utilitaires ============================

    private List<String> effectivePermissionCodes(Admin target) {
        if (isSuper(target)) {
            return permissionRepository.findByActiveTrue().stream()
                    .map(Permission::getCode).sorted().toList();
        }
        Set<Long> ids = userPermissionRepository.findByUserId(target.getId()).stream()
                .map(UserPermission::getPermissionId).collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) {
            return List.of();
        }
        return permissionRepository.findAllById(ids).stream()
                .filter(Permission::isActive)
                .map(Permission::getCode).sorted().toList();
    }

    private RbacUserSummary toSummary(Admin a) {
        return new RbacUserSummary(a.getId(), a.getFirstname(), a.getLastname(), a.getEmail(),
                a.getRole() != null ? a.getRole().name() : null, a.isActive(), isSuper(a), a.getCreatedAt());
    }

    private RbacUserDetail toDetail(Admin a) {
        return new RbacUserDetail(a.getId(), a.getFirstname(), a.getLastname(), a.getEmail(),
                a.getRole() != null ? a.getRole().name() : null, a.isActive(), isSuper(a),
                effectivePermissionCodes(a), a.getCreatedAt(), a.getUpdatedAt());
    }

    private RbacPermissionResponse toPermissionResponse(Permission p) {
        return new RbacPermissionResponse(p.getCode(), p.getLabel(), p.getDescription(), p.getGroupName(),
                p.getType() != null ? p.getType().name() : null, p.isSensitive(), p.isActive());
    }

    private List<String> codesOf(List<Permission> perms) {
        return perms.stream().map(Permission::getCode).toList();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String trimOrNull(String s) {
        return s == null ? null : s.trim();
    }

    private boolean isPasswordStrong(String password) {
        return password != null
                && password.length() >= 8 && password.length() <= 20
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*[0-9].*")
                && password.matches(".*[!@#$%^&*()._-].*");
    }

    private String generateTempPassword() {
        // Génère un mot de passe temporaire fort, conforme à isPasswordStrong (longueur 12).
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digits = "23456789";
        String symbols = "!@#$%^&*._-";
        String all = upper + lower + digits + symbols;

        StringBuilder pw = new StringBuilder();
        pw.append(upper.charAt(secureRandom.nextInt(upper.length())));
        pw.append(lower.charAt(secureRandom.nextInt(lower.length())));
        pw.append(digits.charAt(secureRandom.nextInt(digits.length())));
        pw.append(symbols.charAt(secureRandom.nextInt(symbols.length())));
        for (int i = 4; i < 12; i++) {
            pw.append(all.charAt(secureRandom.nextInt(all.length())));
        }
        return pw.toString();
    }
}
