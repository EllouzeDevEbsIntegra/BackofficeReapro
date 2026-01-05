package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.Admin;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    Optional<Admin> findByEmail(String email);
    Optional<Admin> findByVerificationCode(String code);
    Optional<Admin> findByTempPassword(String tempPassword);
    boolean existsByEmail(String email);

    // AJOUTE CETTE MÉTHODE (pour la recherche dans la liste)
    Page<Admin> findByEmailContainingIgnoreCaseOrFirstnameContainingIgnoreCaseOrLastnameContainingIgnoreCase(
            String email, String firstname, String lastname, Pageable pageable);
}