package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcCustomerListResponse extends BcListResponse<BcCustomerBC> {
}
