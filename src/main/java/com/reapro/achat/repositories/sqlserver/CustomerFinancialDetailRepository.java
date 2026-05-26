package com.reapro.achat.repositories.sqlserver;

import com.reapro.achat.entities.sqlserver.CustomerFinancialDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerFinancialDetailRepository extends JpaRepository<CustomerFinancialDetail, String> {
    Optional<CustomerFinancialDetail> findByCodeClient(String codeClient);
}
