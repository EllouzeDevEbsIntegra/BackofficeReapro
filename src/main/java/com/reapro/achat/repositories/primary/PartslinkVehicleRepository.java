package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.PartslinkVehicle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PartslinkVehicleRepository extends JpaRepository<PartslinkVehicle, Long> {
    Optional<PartslinkVehicle> findByVin(String vin);
}