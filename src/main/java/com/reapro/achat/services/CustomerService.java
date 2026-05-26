package com.reapro.achat.services;

import com.reapro.achat.DTO.bc.BcCustomerBC;
import com.reapro.achat.DTO.bc.BcCustomerListResponse;
import com.reapro.achat.entities.b2bnav.Customer;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.repositories.b2bnav.CustomerRepository;
import com.reapro.achat.repositories.primary.AdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CustomerService {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private BusinessCentralService bcService;

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll(Sort.by(Sort.Direction.ASC, "extId"));
    }

    public List<Customer> getAllCustomersFromBC(String userEmail) {
        Admin admin = adminRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        if (admin.getBcCompanyId() == null || admin.getBcCompanyId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Aucune société BC associée à votre profil.");
        }

        String companyId = admin.getBcCompanyId();

        Map<String, String> queryParams = new HashMap<>();
        // OData filter: blocked eq ' ' (non-blocked customers)
        queryParams.put("$filter", "blocked eq ' '");
        queryParams.put("$orderby", "number asc");

        BcCustomerListResponse resp = bcService.getStandard(
                "customers",
                companyId,
                queryParams,
                BcCustomerListResponse.class
        );

        if (resp == null || resp.getValue() == null) {
            return Collections.emptyList();
        }

        return resp.getValue().stream()
                .map(bcCust -> {
                    Customer c = new Customer();
                    c.setExtId(bcCust.getNumber());
                    c.setCompanyName(bcCust.getDisplayName());
                    c.setPhone(bcCust.getPhoneNumber() != null ? bcCust.getPhoneNumber() : "");
                    c.setEmail(bcCust.getEmail() != null ? bcCust.getEmail() : "");
                    c.setTaxRegistrationNumber(bcCust.getTaxRegistrationNumber() != null ? bcCust.getTaxRegistrationNumber() : "");
                    c.setCity(bcCust.getAddress() != null && bcCust.getAddress().getCity() != null ? bcCust.getAddress().getCity() : "");
                    
                    if (bcCust.getAddress() != null) {
                        StringBuilder sb = new StringBuilder();
                        if (bcCust.getAddress().getStreet() != null && !bcCust.getAddress().getStreet().isBlank()) {
                            sb.append(bcCust.getAddress().getStreet());
                        }
                        if (bcCust.getAddress().getCity() != null && !bcCust.getAddress().getCity().isBlank()) {
                            if (sb.length() > 0) sb.append(", ");
                            sb.append(bcCust.getAddress().getCity());
                        }
                        c.setAddress(sb.toString());
                    } else {
                        c.setAddress("");
                    }
                    return c;
                })
                .collect(Collectors.toList());
    }
}
