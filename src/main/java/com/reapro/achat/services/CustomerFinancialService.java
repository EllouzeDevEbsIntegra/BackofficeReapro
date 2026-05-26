package com.reapro.achat.services;

import com.reapro.achat.DTO.CustomerFinancialDetailResponse;
import com.reapro.achat.entities.sqlserver.CustomerFinancialDetail;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import com.reapro.achat.repositories.sqlserver.CustomerFinancialDetailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomerFinancialService {

    private final CustomerFinancialDetailRepository customerFinancialDetailRepository;

    @Transactional(readOnly = true)
    public CustomerFinancialDetailResponse getCustomerFinancialDetails(String codeClient) {
        CustomerFinancialDetail detail = customerFinancialDetailRepository.findByCodeClient(codeClient)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, "Détails financiers du client introuvables pour le code : " + codeClient));

        return mapToDto(detail);
    }

    private CustomerFinancialDetailResponse mapToDto(CustomerFinancialDetail detail) {
        BigDecimal commandeOuverte = Optional.ofNullable(detail.getCommandeOuverte()).orElse(BigDecimal.ZERO);
        BigDecimal livreNonFacture = Optional.ofNullable(detail.getLivreNonFacture()).orElse(BigDecimal.ZERO);
        BigDecimal retourNonFacture = Optional.ofNullable(detail.getRetourNonFacture()).orElse(BigDecimal.ZERO);
        BigDecimal factureNonReglee = Optional.ofNullable(detail.getFactureNonReglee()).orElse(BigDecimal.ZERO);
        BigDecimal avoirNonReglee = Optional.ofNullable(detail.getAvoirNonReglee()).orElse(BigDecimal.ZERO);
        BigDecimal traiteEnCoffre = Optional.ofNullable(detail.getTraiteEnCoffre()).orElse(BigDecimal.ZERO);
        BigDecimal chequeEnCoffre = Optional.ofNullable(detail.getChequeEnCoffre()).orElse(BigDecimal.ZERO);

        return CustomerFinancialDetailResponse.builder()
                .codeClient(detail.getCodeClient())
                .plafondCommercial(detail.getCreditLimitLcy())
                .encoursEncaissementEnCoffre(detail.getEncoursEncaissementEnCoffre())
                .contreRemboursement(detail.getContreRemboursement() != null && detail.getContreRemboursement() == 1)
                .encoursCommercial(commandeOuverte.add(livreNonFacture).subtract(retourNonFacture))
                .factureEtAvoirs(factureNonReglee.subtract(avoirNonReglee))
                .encoursFinancier(traiteEnCoffre.add(chequeEnCoffre))
                .build();
    }
}
