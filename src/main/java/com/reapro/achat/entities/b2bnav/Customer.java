package com.reapro.achat.entities.b2bnav;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer", schema = "public")
@Getter
@Setter
public class Customer {

    @Id
    @Column(name = "ext_id")
    private String extId;

    @Column(name = "address")
    private String address;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "phone")
    private String phone;

    @Transient
    private String email;

    @Transient
    private String taxRegistrationNumber;

    @Transient
    private String city;
}
