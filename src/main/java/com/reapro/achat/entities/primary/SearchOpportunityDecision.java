package com.reapro.achat.entities.primary;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "search_opportunity_decision")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchOpportunityDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "normalized_filter", nullable = false)
    private String normalizedFilter;

    @Column(name = "original_filter_example")
    private String originalFilterExample;

    @Column(name = "decision_date", nullable = false)
    private LocalDateTime decisionDate;

    @Column(name = "decided_by", nullable = false)
    private String decidedBy;

    @Column(name = "diagnostic_status", nullable = false)
    private String diagnosticStatus;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "closure_reason", length = 500)
    private String closureReason;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "linked_article_id")
    private Long linkedArticleId;

    @Column(name = "closed_batch_id", nullable = false)
    private UUID closedBatchId;

    @Column(name = "closed_rows_count", nullable = false)
    private Integer closedRowsCount;

    @Column(name = "first_search_date")
    private LocalDateTime firstSearchDate;

    @Column(name = "last_search_date")
    private LocalDateTime lastSearchDate;

    @Column(name = "distinct_customers_count")
    private Integer distinctCustomersCount;

    @Column(name = "total_attempts")
    private Integer totalAttempts;

    @Column(name = "zero_result_count")
    private Integer zeroResultCount;

    @Column(name = "no_stock_count")
    private Integer noStockCount;
}
