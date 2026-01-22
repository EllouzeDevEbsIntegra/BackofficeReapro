package com.reapro.achat.Controller;

import com.reapro.achat.entities.b2bnav.Customer;
import com.reapro.achat.services.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
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
