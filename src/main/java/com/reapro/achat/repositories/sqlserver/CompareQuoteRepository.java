// src/main/java/com/reapro/achat/repositories/sqlserver/CompareQuoteRepository.java

package com.reapro.achat.repositories.sqlserver;

import com.reapro.achat.entities.sqlserver.CompareQuote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompareQuoteRepository extends JpaRepository<CompareQuote, String> {

    // Liste complète paginée
    Page<CompareQuote> findAll(Pageable pageable);

    @Query("SELECT c FROM CompareQuote c WHERE " +
            "LOWER(c.no) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(c.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<CompareQuote> search(@Param("search") String search, Pageable pageable);

    // Filtre par statut
    List<CompareQuote> findByStatus(Integer status);

    // Derniers créés
    List<CompareQuote> findTop10ByOrderByCreationDateDesc();
}