package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchOpportunityCustomerResponse {
    private Long customerId;
    private String extId;
    private String companyName;
    private long attempts;
}
