package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BcItemStockBC {

    @JsonProperty("no")
    private String no;

    @JsonProperty("qtyStock")
    private BigDecimal qtyStock;

    @JsonProperty("lastPurshDate")
    private LocalDate lastPurshDate;
}