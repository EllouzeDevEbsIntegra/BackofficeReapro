package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.PartslinkSchematic;
import com.reapro.achat.entities.primary.PartslinkSubgroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartslinkSchematicRepository extends JpaRepository<PartslinkSchematic, Long> {
    Optional<PartslinkSchematic> findBySubgroup(PartslinkSubgroup subgroup);
}