package com.reapro.achat.Controller;

import com.reapro.achat.entities.b2bnav.Customer;
import com.reapro.achat.services.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// RBAC Lot 4 : clients (consommés par le module B2B) → B2B_ACCESS (super-admin bypass via authorities).
@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasAuthority('B2B_ACCESS')")
public class CustomerController {

    @Autowired
    private CustomerService customerService;

    @GetMapping("/customerB2B")
    public List<Customer> getAllCustomersLocal() {
        return customerService.getAllCustomers();
    }

    @GetMapping
    public List<Customer> getAllCustomersFromBC(@AuthenticationPrincipal String email) {
        return customerService.getAllCustomersFromBC(email);
    }
}
