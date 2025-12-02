package com.backoffice.atelier.repositories;

import com.backoffice.atelier.entities.Admin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByEmail(String email);

    Optional<Admin> findByVerificationCode(String code);

    Optional<Admin> findByTempPassword(String tempPassword);

    boolean existsByEmail(String email);
}
