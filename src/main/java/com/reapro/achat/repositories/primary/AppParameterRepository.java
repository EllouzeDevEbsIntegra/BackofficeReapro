package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.AppParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface AppParameterRepository extends JpaRepository<AppParameter, Long> {
    Optional<AppParameter> findByParamKeyAndActiveTrue(String paramKey);
    boolean existsByParamKey(String paramKey);
}