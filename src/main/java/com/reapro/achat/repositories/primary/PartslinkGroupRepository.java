package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.PartslinkGroup;
import com.reapro.achat.entities.primary.PartslinkVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PartslinkGroupRepository extends JpaRepository<PartslinkGroup, Long> {
    List<PartslinkGroup> findByVehicle(PartslinkVehicle vehicle);
    Optional<PartslinkGroup> findByVehicleAndCode(PartslinkVehicle vehicle, String code);
}