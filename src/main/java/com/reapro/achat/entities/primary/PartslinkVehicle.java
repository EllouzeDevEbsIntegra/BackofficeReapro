package com.reapro.achat.entities.primary;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "partslink_vehicles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartslinkVehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 17)
    private String vin;

    @Column(name = "model_designation")
    private String modelDesignation;

    @Column(name = "production_date", length = 50)
    private String productionDate;

    @Column(length = 150)
    private String color;

    @Column(length = 255)
    private String upholstery;

    @Column(length = 100)
    private String transmission;

    @Column(name = "model_code", length = 100)
    private String modelCode;

    @Column(length = 150)
    private String model;

    /** Partslink URL brand segment, e.g. "bmw", "mercedes-benz", "volkswagen" */
    @Column(name = "brand_code", length = 100)
    private String brandCode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}