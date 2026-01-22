package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.ElvaItemCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ElvaItemCacheRepository extends JpaRepository<ElvaItemCache, String>, JpaSpecificationExecutor<ElvaItemCache> {
    java.util.List<ElvaItemCache> findByReferenceOrigineLieAndNoNot(String referenceOrigineLie, String no);
}
