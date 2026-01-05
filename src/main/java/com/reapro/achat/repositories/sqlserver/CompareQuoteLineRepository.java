// src/main/java/com/reapro/achat/repositories/sqlserver/CompareQuoteLineRepository.java

package com.reapro.achat.repositories.sqlserver;

import com.reapro.achat.entities.sqlserver.CompareQuoteLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CompareQuoteLineRepository extends JpaRepository<CompareQuoteLine, String>, JpaSpecificationExecutor<CompareQuoteLine> {
}