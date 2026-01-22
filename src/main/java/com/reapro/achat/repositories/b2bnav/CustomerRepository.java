package com.reapro.achat.repositories.b2bnav;

import com.reapro.achat.entities.b2bnav.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, String> {
}
