package com.reapro.achat.repositories.sqlserver;

import com.reapro.achat.entities.sqlserver.ElvaItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ElvaItemRepository extends JpaRepository<ElvaItem, String>, JpaSpecificationExecutor<ElvaItem> {

    // You specifically asked for SELECT TOP 1000, so here is a custom query for it.
    // However, it's better practice to use Pageable for pagination.
    @Query(value = "SELECT TOP 1000 * FROM ELVA_Item", nativeQuery = true)
    List<ElvaItem> findTop1000();

    // Standard pagination method
    Page<ElvaItem> findAll(Pageable pageable);

    // Look up a single item by its reference (No_) for real-time stock checking
    Optional<ElvaItem> findByNo(String no);
}
