package com.backoffice.atelier.services;

import com.backoffice.atelier.DTO.*;
import com.backoffice.atelier.config.JwtUtils;
import com.backoffice.atelier.entities.Admin;
import com.backoffice.atelier.repositories.AdminRepository;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

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

    // 1️⃣ REGISTER (création compte + code envoyé)
    public String register(RegisterRequest request) {

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
                .role("ADMIN")
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

        Admin admin = adminRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Identifiants invalides."));

        if (!admin.isActive()) {
            throw new RuntimeException("Compte non activé. Vérifiez votre email.");
        }

        if (!passwordEncoder.matches(request.getPassword(), admin.getPassword())) {
            throw new RuntimeException("Identifiants invalides.");
        }

        String accessToken = jwtUtils.generateAccessToken(admin.getEmail());
        String refreshToken = jwtUtils.generateRefreshToken(admin.getEmail());

        return new TokenResponse(accessToken, refreshToken);
    }

    // 4️⃣ REFRESH TOKEN
    public TokenResponse refreshToken(String refreshToken) {

        String email = jwtUtils.extractEmailFromRefreshToken(refreshToken);

        if (email == null) {
            throw new RuntimeException("Refresh token invalide ou expiré.");
        }

        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

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
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable."));

        if (!admin.isActive()) {
            return "Compte non activé.";
        }

        if (!passwordEncoder.matches(req.getOldPassword(), admin.getPassword())) {
            return "Ancien mot de passe incorrect.";
        }

        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            return "La confirmation du mot de passe ne correspond pas.";
        }

        if (!isPasswordStrong(req.getNewPassword())) {
            return "Le mot de passe doit contenir 8-20 caractères, avec majuscule, minuscule, chiffre et symbole.";
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
