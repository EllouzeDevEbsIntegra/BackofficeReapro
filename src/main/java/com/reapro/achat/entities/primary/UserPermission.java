package com.reapro.achat.entities.primary;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Affectation d'une {@link Permission} à un utilisateur ({@code admins.id}).
 *
 * <p>Volontairement modélisée avec des clés étrangères « plates » ({@code userId} / {@code permissionId})
 * plutôt que des associations JPA, pour rester découplée et éviter tout chargement paresseux inattendu.
 * Les contraintes FK + unicité sont posées en base par {@code V9__Create_permissions_tables.sql}.</p>
 */
@Entity
@Table(
        name = "user_permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_permission",
                columnNames = {"user_id", "permission_id"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    /** Id de l'administrateur ayant accordé la permission (nullable : affectations système / migration). */
    @Column(name = "granted_by")
    private Long grantedBy;

    @Column(name = "granted_at")
    private LocalDateTime grantedAt;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
    }
}
