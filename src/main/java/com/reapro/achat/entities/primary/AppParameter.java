package com.reapro.achat.entities.primary;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "app_parameters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppParameter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String paramKey;

    @Column(nullable = false, length = 500)
    private String paramValue;

    @Column(length = 255)
    private String description;

    @Column(length = 50)
    private String category;

    private boolean encrypted;
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        active = true;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}