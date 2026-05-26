package com.reapro.achat.entities.primary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "search_history_sync")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalSearchHistory {

    @Id
    private Long id;

    @Column(columnDefinition = "TEXT")
    private String filterDecoded;

    private LocalDateTime creationDate;

    private String type;

    @Column(name = "customer_id")
    private Long customerId; // NOUVEAU: Identifiant source B2B

    @Column(name = "customer_ext_id")
    private String customerExtId;

    private String companyName;

    private Integer resultsCount;
    
    private Boolean isStockAvailable;
    
    private LocalDateTime syncedAt;

    // --- CHAMPS D'ANALYSE / CLÔTURE ---

    @Column(name = "normalized_filter", length = 255)
    private String normalizedFilter;

    @Column(name = "is_closed", nullable = false)
    @Builder.Default
    private Boolean isClosed = false;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "closure_reason", length = 500)
    private String closureReason;

    @Column(name = "diagnostic_status", length = 100)
    private String diagnosticStatus;

    @Column(name = "diagnostic_comment", columnDefinition = "TEXT")
    private String diagnosticComment;

    @Column(name = "action_type", length = 100)
    private String actionType;

    @Column(name = "linked_article_id")
    private Long linkedArticleId;

    @Column(name = "closed_batch_id")
    private UUID closedBatchId;
}
