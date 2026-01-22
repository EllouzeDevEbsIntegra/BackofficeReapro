package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.SalesOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, Long> {

    Optional<SalesOrderLine> findBySalesOrderIdAndItemReference(Long salesOrderId, String itemReference);
}
