package com.reapro.achat.entities.primary;

import com.reapro.achat.enums.PermissionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Permission RBAC déclarée dans le registre {@code PermissionCode} et synchronisée en base par le seeder.
 * Table créée via la migration SQL {@code V9__Create_permissions_tables.sql} (Flyway non actif → exécution manuelle).
 */
@Entity
@Table(name = "permissions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column(nullable = false, length = 150)
    private String label;

    @Column(length = 255)
    private String description;

    @Column(name = "group_name", length = 80)
    private String groupName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PermissionType type;

    @Builder.Default
    @Column(nullable = false)
    private boolean sensitive = false;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
