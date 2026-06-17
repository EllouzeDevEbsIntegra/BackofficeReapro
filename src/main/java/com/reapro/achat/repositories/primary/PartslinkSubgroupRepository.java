package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.PartslinkGroup;
import com.reapro.achat.entities.primary.PartslinkSubgroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartslinkSubgroupRepository extends JpaRepository<PartslinkSubgroup, Long> {
    List<PartslinkSubgroup> findByGroup(PartslinkGroup group);
    Optional<PartslinkSubgroup> findByGroupAndCode(PartslinkGroup group, String code);
}