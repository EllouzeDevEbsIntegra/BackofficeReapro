package com.reapro.achat.services;

import com.reapro.achat.DTO.*;
import com.reapro.achat.DTO.bc.BcCompanyBC;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.repositories.primary.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final AdminRepository adminRepository;
    private final BcCompanyService bcCompanyService;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private Admin getAdminByEmail(String email) {
        return adminRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));
    }

    // 1) Profil personnel
    public AdminProfileResponse getMyProfile(String email) {
        Admin admin = getAdminByEmail(email);
        return new AdminProfileResponse(
                admin.getId(),
                admin.getFirstname(),
                admin.getLastname(),
                admin.getEmail(),
                admin.isActive(),
                admin.getRole().name(),
                admin.getBcCompanyId(),
                admin.getBcCompanyName(),
                admin.getCreatedAt()
        );
    }

    // 2) Mettre à jour son profil
    public String updateMyProfile(String email, UpdateProfileRequest request) {
        Admin admin = getAdminByEmail(email);

        if (request.getFirstname() != null && !request.getFirstname().isBlank()) {
            admin.setFirstname(request.getFirstname().trim());
        }
        if (request.getLastname() != null && !request.getLastname().isBlank()) {
            admin.setLastname(request.getLastname().trim());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equals(admin.getEmail()) && adminRepository.existsByEmail(newEmail)) {
                return "Cet email est déjà utilisé par un autre compte.";
            }
            admin.setEmail(newEmail);
        }

        adminRepository.save(admin);
        return "Profil mis à jour avec succès.";
    }

    // 2bis) Mettre à jour la société BC du profil (validation via BC /companies)
    public AdminProfileResponse updateMyCompany(String email, UpdateMyCompanyRequest request) {
        Admin admin = getAdminByEmail(email);

        if (request == null || request.getBcCompanyId() == null || request.getBcCompanyId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bcCompanyId est obligatoire.");
        }

        String companyId = request.getBcCompanyId().trim();

        // Charger les sociétés depuis BC et valider l'id
        BcCompanyBC company = bcCompanyService.getCompanies().stream()
                .filter(c -> c.getId() != null && c.getId().equalsIgnoreCase(companyId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Société BC introuvable: " + companyId
                ));

        admin.setBcCompanyId(company.getId());

        // displayName si dispo, sinon name
        String companyName = (company.getDisplayName() != null && !company.getDisplayName().isBlank())
                ? company.getDisplayName().trim()
                : (company.getName() != null ? company.getName().trim() : null);

        admin.setBcCompanyName(companyName);

        adminRepository.save(admin);

        return getMyProfile(email);
    }

    // 3) Changer mot de passe
    public String changeMyPassword(String email, ChangeMyPasswordRequest request) {
        Admin admin = getAdminByEmail(email);

        if (!passwordEncoder.matches(request.getOldPassword(), admin.getPassword())) {
            return "Ancien mot de passe incorrect.";
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return "Les mots de passe ne correspondent pas.";
        }
        if (!isPasswordStrong(request.getNewPassword())) {
            return "Le mot de passe doit contenir 8-20 caractères, avec majuscule, minuscule, chiffre et symbole.";
        }

        admin.setPassword(passwordEncoder.encode(request.getNewPassword()));
        adminRepository.save(admin);
        return "Mot de passe modifié avec succès.";
    }

    // 4) Lister tous les admins
    public Page<AdminListResponse> getAllAdmins(Pageable pageable, String search) {
        if (search == null || search.trim().isEmpty()) {
            return adminRepository.findAll(pageable).map(this::toListResponse);
        }

        String s = search.trim();
        return adminRepository
                .findByEmailContainingIgnoreCaseOrFirstnameContainingIgnoreCaseOrLastnameContainingIgnoreCase(
                        s, s, s, pageable
                )
                .map(this::toListResponse);
    }

    private AdminListResponse toListResponse(Admin admin) {
        return new AdminListResponse(
                admin.getId(),
                admin.getFirstname(),
                admin.getLastname(),
                admin.getEmail(),
                admin.isActive(),
                admin.getRole().name(),
                admin.getBcCompanyId(),
                admin.getBcCompanyName(),
                admin.getCreatedAt()
        );
    }

    // 5) Détail d’un admin
    public AdminDetailResponse getAdminById(Long id) {
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Admin introuvable."));

        return new AdminDetailResponse(
                admin.getId(),
                admin.getFirstname(),
                admin.getLastname(),
                admin.getEmail(),
                admin.isActive(),
                admin.getRole().name(),
                admin.getBcCompanyId(),
                admin.getBcCompanyName(),
                admin.getCreatedAt(),
                admin.getUpdatedAt()
        );
    }

    // 6) Mettre à jour un admin (ADMIN seulement)
    public String updateAdmin(Long id, UpdateAdminRequest request) {
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Admin introuvable."));

        if (request.getFirstname() != null && !request.getFirstname().isBlank()) {
            admin.setFirstname(request.getFirstname().trim());
        }
        if (request.getLastname() != null && !request.getLastname().isBlank()) {
            admin.setLastname(request.getLastname().trim());
        }
        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            String newEmail = request.getEmail().trim().toLowerCase();
            if (!newEmail.equals(admin.getEmail()) && adminRepository.existsByEmail(newEmail)) {
                return "Cet email est déjà utilisé.";
            }
            admin.setEmail(newEmail);
        }
        if (request.getRole() != null) {
            admin.setRole(request.getRole());
        }

        adminRepository.save(admin);
        return "Administrateur mis à jour avec succès.";
    }

    // 7) Toggle active
    public String toggleAdminActive(Long id) {
        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Admin introuvable."));
        admin.setActive(!admin.isActive());
        adminRepository.save(admin);
        return admin.isActive()
                ? "Administrateur activé avec succès."
                : "Administrateur désactivé avec succès.";
    }

    private boolean isPasswordStrong(String password) {
        return password != null
                && password.length() >= 8 && password.length() <= 20
                && password.matches(".*[A-Z].*")
                && password.matches(".*[a-z].*")
                && password.matches(".*[0-9].*")
                && password.matches(".*[!@#$%^&*()._-].*");
    }
}