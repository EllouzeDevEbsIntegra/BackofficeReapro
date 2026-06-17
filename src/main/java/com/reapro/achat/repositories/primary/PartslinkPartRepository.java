package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.PartslinkPart;
import com.reapro.achat.entities.primary.PartslinkSubgroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartslinkPartRepository extends JpaRepository<PartslinkPart, Long> {
    List<PartslinkPart> findBySubgroup(PartslinkSubgroup subgroup);
}