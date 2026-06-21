package com.reapro.achat.services;

import com.reapro.achat.DTO.*;
import com.reapro.achat.config.JwtUtils;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.enums.Role;
import com.reapro.achat.repositories.primary.AdminRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminRepository adminRepository;
    private final EmailService emailService;
    private final JwtUtils jwtUtils;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * Inscription publique : désactivée par défaut (sécurité). À n'activer qu'en dev/contrôlé.
     * Quand désactivée : aucun compte n'est créé via les endpoints publics.
     */
    @Value("${app.auth.registration-enabled:false}")
    private boolean registrationEnabled;

    // 1️⃣ REGISTER (création compte + code envoyé)
    public String register(RegisterRequest request) {

        // Sécurité : pas d'inscription publique libre. Aucun utilisateur créé si désactivée.
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "L'inscription publique est désactivée.");
        }

        if (adminRepository.existsByEmail(request.getEmail())) {
            return "Cet email est déjà utilisé.";
        }

        if (!request.getPassword().equals(request.getConfirmPassword())) {
            return "La confirmation du mot de passe ne correspond pas.";
        }

        if (!isPasswordStrong(request.getPassword())) {
            return "Le mot de passe doit contenir 8-20 caractères, avec majuscule, minuscule, chiffre et symbole.";
        }

        String code = generate4DigitCode();

        Admin admin = Admin.builder()
                .firstname(request.getFirstname())
                .lastname(request.getLastname())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .verificationCode(code)
                .verificationExpireAt(LocalDateTime.now().plusMinutes(10))
                .active(false)
                // Sécurité : un endpoint public ne crée JAMAIS un compte privilégié.
                .role(Role.ROLE_USER)
                .build();

        adminRepository.save(admin);

        try {
            emailService.sendVerificationCodeHtml(
                    admin.getEmail(),
                    admin.getFirstname(),
                    code
            );
        } catch (MessagingException e) {
            e.printStackTrace();
            return "Compte créé mais erreur lors de l'envoi de l'email.";
        }

        return "Code de vérification envoyé à votre email.";
    }

    // 2️⃣ Vérifier le code d’inscription
    public String verifyRegisterCode(VerifyCodeRequest request) {

        // Le flux d'inscription publique est désactivé : on ne valide aucune création de compte.
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "L'inscription publique est désactivée.");
        }

        Optional<Admin> optAdmin = adminRepository.findByEmail(request.getEmail());
        if (optAdmin.isEmpty()) {
            return "Compte introuvable pour cet email.";
        }

        Admin admin = optAdmin.get();

        // ——————————————————————
        // 🔥 1. Code expiré → renvoyer un nouveau code automatiquement
        // ——————————————————————
        if (admin.getVerificationExpireAt() == null ||
                admin.getVerificationExpireAt().isBefore(LocalDateTime.now())) {

            String newCode = generate4DigitCode();

            admin.setVerificationCode(newCode);
            admin.setVerificationExpireAt(LocalDateTime.now().plusMinutes(10));
            adminRepository.save(admin);

            try {
                emailService.sendVerificationCodeHtml(
                        admin.getEmail(),
                        admin.getFirstname(),
                        newCode
                );
            } catch (Exception e) {
                return "Code expiré. Impossible d'envoyer un nouveau code.";
            }

            return "Code expiré. Un nouveau code vous a été envoyé.";
        }

        // ——————————————————————
        // 🔥 2. Vérifier si le code est bon
        // ——————————————————————
        if (!admin.getVerificationCode().equals(request.getCode())) {
            return "Code invalide.";
        }

        // ——————————————————————
        // 🔥 3. Activer le compte
        // ——————————————————————
        admin.setActive(true);
        admin.setVerificationCode(null);
        admin.setVerificationExpireAt(null);
        adminRepository.save(admin);

        return "Compte créé et vérifié avec succès.";
    }
    // 3️⃣ LOGIN
    public TokenResponse login(LoginRequest request) {

        // 401 : identité non vérifiée / session non établie (sémantique auth). Aucun token loggé.
        Admin admin = adminRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides."));

        // Utilisateur désactivé / non activé → login refusé (401), avant toute génération de token.
        if (!admin.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Compte désactivé.");
        }

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Identifiants invalides.");
        }

        String accessToken = jwtUtils.generateAccessToken(admin.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(admin.getEmail());

        return new TokenResponse(accessToken, refreshToken);
    }

    // 4️⃣ REFRESH TOKEN
    public TokenResponse refreshToken(String refreshToken) {

        String email = jwtUtils.extractEmailFromRefreshToken(refreshToken);

        // 401 : refresh token absent / invalide / expiré → la session ne peut pas être prolongée.
        if (email == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré.");
        }

        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token invalide ou expiré."));

        // Utilisateur désactivé → impossible de rafraîchir un token (401).
        if (!admin.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Compte désactivé.");
        }

        String newAccessToken = jwtUtils.generateAccessToken(admin.getEmail());
        String newRefreshToken = jwtUtils.generateRefreshToken(admin.getEmail());

        return new TokenResponse(newAccessToken, newRefreshToken);
    }

    // 5️⃣ FORGOT PASSWORD → envoi code
    public String forgotPassword(ForgotPasswordRequest request) {

        Optional<Admin> optAdmin = adminRepository.findByEmail(request.getEmail());
        if (optAdmin.isEmpty()) {
            return "Si cet email existe, un code vous a été envoyé.";
        }

        Admin admin = optAdmin.get();

        if (!admin.isActive()) {
            return "Veuillez activer votre compte avant de réinitialiser le mot de passe.";
        }

        String code = generate4DigitCode();
        admin.setVerificationCode(code);
        admin.setVerificationExpireAt(LocalDateTime.now().plusMinutes(10));

        adminRepository.save(admin);

        try {
            emailService.sendVerificationCodeHtml(
                    admin.getEmail(),
                    admin.getFirstname(),
                    code
            );
        } catch (MessagingException e) {
            return "Erreur lors de l'envoi du mail.";
        }

        return "Si cet email existe, un code de réinitialisation a été envoyé.";
    }

    // 6️⃣ Vérifier code de reset + envoyer mot de passe temporaire
    public String verifyResetCode(VerifyCodeRequest request) throws MessagingException {

        Optional<Admin> optAdmin = adminRepository.findByEmail(request.getEmail());
        if (optAdmin.isEmpty()) {
            return "Compte introuvable.";
        }

        Admin admin = optAdmin.get();

        if (!admin.isActive()) {
            return "Compte non activé.";
        }

        // Code expiré → nouveau code
        if (admin.getVerificationExpireAt() != null &&
                admin.getVerificationExpireAt().isBefore(LocalDateTime.now())) {

            String newCode = generate4DigitCode();
            admin.setVerificationCode(newCode);
            admin.setVerificationExpireAt(LocalDateTime.now().plusMinutes(10));
            adminRepository.save(admin);

            emailService.sendVerificationCodeHtml(
                    admin.getEmail(),
                    admin.getFirstname(),
                    newCode
            );

            return "Code expiré. Un nouveau code a été envoyé.";
        }

        if (admin.getVerificationCode() == null ||
                !admin.getVerificationCode().equals(request.getCode())) {
            return "Code de vérification invalide.";
        }

        // Générer mot de passe temporaire
        String tempPassword = generateTempPassword();

        admin.setPassword(passwordEncoder.encode(tempPassword));
        admin.setVerificationCode(null);
        admin.setVerificationExpireAt(null);
        admin.setTempPasswordExpireAt(LocalDateTime.now().plusHours(1));

        adminRepository.save(admin);

        // Email HTML avec le mot de passe temporaire
        emailService.sendTempPasswordHtml(
                admin.getEmail(),
                admin.getFirstname(),
                tempPassword
        );

        return "Un mot de passe temporaire a été envoyé à votre email.";
    }

    // 7️⃣ Changer mot de passe connecté
    public String changePassword(String email, ChangePasswordRequest req) throws MessagingException {

        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        if (!admin.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Compte non activé.");
        }

        if (!passwordEncoder.matches(req.getOldPassword(), admin.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Ancien mot de passe incorrect.");
        }

        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "La confirmation du mot de passe ne correspond pas.");
        }

        if (!isPasswordStrong(req.getNewPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Le mot de passe doit contenir 8-20 caractères, avec majuscule, minuscule, chiffre et symbole.");
        }

        admin.setPassword(passwordEncoder.encode(req.getNewPassword()));
        admin.setTempPasswordExpireAt(null);
        adminRepository.save(admin);

        emailService.sendPasswordChangedHtml(
                admin.getEmail(),
                admin.getFirstname()
        );

        return "Mot de passe modifié avec succès.";
    }

    // =========================
    // 🔧 UTILITAIRES
    // =========================

    public String generate4DigitCode() {
        Random r = new Random();
        return String.format("%04d", r.nextInt(10000));
    }

    public String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
        StringBuilder pw = new StringBuilder();
        Random r = new Random();

        for (int i = 0; i < 10; i++) {
            pw.append(chars.charAt(r.nextInt(chars.length())));
        }
        return pw.toString();
    }

    private boolean isPasswordStrong(String password) {
        return password != null
                && password.length() >= 8
                && password.length() <= 20
                && password.matches(".*[A-Z].*")          // majuscule
                && password.matches(".*[a-z].*")          // minuscule
                && password.matches(".*[0-9].*")          // chiffre
                && password.matches(".*[!@#$%^&*()._-].*"); // symbole
    }
}
