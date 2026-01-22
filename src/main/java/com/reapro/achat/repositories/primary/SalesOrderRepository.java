package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.entities.primary.SalesOrder;
import com.reapro.achat.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    Optional<SalesOrder> findByCreatedByAndClientIdAndStatus(Admin createdBy, String clientId, OrderStatus status);

    Optional<SalesOrder> findByLocalNumber(String localNumber);

    List<SalesOrder> findByStatusAndBusinessCentralOrderNumberIsNotNull(OrderStatus status);

    // Pour l'historique paginé par utilisateur
    Page<SalesOrder> findByCreatedByOrderByCreatedAtDesc(Admin createdBy, Pageable pageable);

    // Pour l'historique filtré par client
    Page<SalesOrder> findByCreatedByAndClientIdOrderByCreatedAtDesc(Admin createdBy, String clientId, Pageable pageable);

    // Pour l'historique filtré par statut
    Page<SalesOrder> findByCreatedByAndStatusOrderByCreatedAtDesc(Admin createdBy, OrderStatus status, Pageable pageable);
}
