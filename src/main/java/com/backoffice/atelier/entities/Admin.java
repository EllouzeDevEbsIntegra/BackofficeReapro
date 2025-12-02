package com.backoffice.atelier.entities;

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

    private String password; // mot de passe hashé (BCrypt)

    private boolean active = true;

    private String role = "ADMIN";

    // Code de vérification (pour inscription ou reset)
    private String verificationCode;

    private LocalDateTime verificationExpireAt;

    // Mot de passe temporaire (pour mot de passe oublié)
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
