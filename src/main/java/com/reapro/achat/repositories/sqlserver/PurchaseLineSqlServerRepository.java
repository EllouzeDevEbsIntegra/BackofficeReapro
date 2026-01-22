package com.reapro.achat.repositories.sqlserver;

import com.reapro.achat.entities.sqlserver.PurchaseLineSqlServer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseLineSqlServerRepository extends JpaRepository<PurchaseLineSqlServer, String> {
    Page<PurchaseLineSqlServer> findByNo(String no, Pageable pageable);
}
