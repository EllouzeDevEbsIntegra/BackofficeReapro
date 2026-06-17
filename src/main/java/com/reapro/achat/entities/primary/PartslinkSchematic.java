package com.reapro.achat.entities.primary;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "partslink_schematics")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartslinkSchematic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subgroup_id", nullable = false, unique = true)
    private PartslinkSubgroup subgroup;

    @Column(name = "image_path", nullable = false, length = 500)
    private String imagePath;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}