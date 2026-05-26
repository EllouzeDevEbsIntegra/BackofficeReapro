package com.reapro.achat.entities.primary;

import com.reapro.achat.enums.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "admins")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Admin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Informations personnelles
    private String firstname;
    private String lastname;

    @Column(unique = true, nullable = false)
    private String email;

    private String password;

    @Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Role role = Role.ROLE_ADMIN;

    // ✅ NOUVEAU : Société BC de l'utilisateur
    @Column(name = "bc_company_id", length = 36) // GUID
    private String bcCompanyId;

    // ✅ NOUVEAU : Nom société (displayName ou name) - optionnel mais pratique
    @Column(name = "bc_company_name", length = 120)
    private String bcCompanyName;

    // Code de vérification
    private String verificationCode;
    private LocalDateTime verificationExpireAt;

    // Mot de passe temporaire
    private String tempPassword;
    private LocalDateTime tempPasswordExpireAt;

    // Dates d'audit
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
