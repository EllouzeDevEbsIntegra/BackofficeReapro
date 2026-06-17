package com.reapro.achat.entities.primary;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "partslink_parts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartslinkPart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subgroup_id", nullable = false)
    private PartslinkSubgroup subgroup;

    @Column(length = 50)
    private String position;

    @Column(name = "part_number", length = 100)
    private String partNumber;

    @Column(length = 500)
    private String designation;

    @Column(name = "info_suppl", columnDefinition = "TEXT")
    private String infoSuppl;

    @Column(length = 50)
    private String quantity;

    @Column(length = 100)
    private String ae;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}