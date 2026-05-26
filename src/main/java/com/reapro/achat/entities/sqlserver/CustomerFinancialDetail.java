package com.reapro.achat.entities.sqlserver;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Entity
@Table(name = "View_Cust_Financial_Details", schema = "dbo")
@Immutable
@Data
public class CustomerFinancialDetail {

    @Id
    @Column(name = "Code Client")
    private String codeClient;

    @Column(name = "Credit Limit (LCY)")
    private BigDecimal creditLimitLcy;

    @Column(name = "Encours encaissement en coffre")
    private BigDecimal encoursEncaissementEnCoffre;

    @Column(name = "Contre remboursement")
    private Integer contreRemboursement; // Usually tinyint in SQL Server for boolean

    @Column(name = "Commande ouverte")
    private BigDecimal commandeOuverte;

    @Column(name = "Livré non facturé")
    private BigDecimal livreNonFacture;

    @Column(name = "retourNonfacturé")
    private BigDecimal retourNonFacture;

    @Column(name = "traiteEnCoffre")
    private BigDecimal traiteEnCoffre;

    @Column(name = "chequeEnCoffre")
    private BigDecimal chequeEnCoffre;

    @Column(name = "FactureNonRéglée")
    private BigDecimal factureNonReglee;

    @Column(name = "AvoirNonRéglée")
    private BigDecimal avoirNonReglee;
}
