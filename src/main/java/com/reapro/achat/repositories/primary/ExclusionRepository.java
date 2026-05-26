package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.Exclusion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;

@Repository
public interface ExclusionRepository extends JpaRepository<Exclusion, Long> {
    
    boolean existsByNormalizedFilter(String normalizedFilter);

    Optional<Exclusion> findByNormalizedFilter(String normalizedFilter);

    @Query("SELECT e.normalizedFilter FROM Exclusion e")
    Set<String> findAllNormalizedFilters();
}
