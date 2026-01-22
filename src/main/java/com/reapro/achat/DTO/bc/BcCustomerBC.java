package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcCustomerBC {

    @JsonProperty("id")
    private String id;

    @JsonProperty("number")
    private String number;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("phoneNumber")
    private String phoneNumber;

    @JsonProperty("email")
    private String email;

    @JsonProperty("blocked")
    private String blocked;

    @JsonProperty("address")
    private BcCustomerAddress address;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BcCustomerAddress {
        @JsonProperty("street")
        private String street;
        
        @JsonProperty("city")
        private String city;
        
        @JsonProperty("state")
        private String state;
        
        @JsonProperty("postalCode")
        private String postalCode;
    }
}
