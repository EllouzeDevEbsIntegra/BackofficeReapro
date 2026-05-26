package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.SearchOpportunityDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SearchOpportunityDecisionRepository extends JpaRepository<SearchOpportunityDecision, Long> {
    boolean existsByDiagnosticStatus(String diagnosticStatus);
    boolean existsByActionType(String actionType);
}
