package com.reapro.achat.DTO.bc;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Champs modifiables sur une ligne de devis BC.
 * On n'envoie QUE les champs non nuls à BC (PATCH partiel).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class QuoteLineUpdateRequest {

    private BigDecimal askingPrice;    // "askingPrice"
    private BigDecimal askingQty;      // "askingQty"
    private BigDecimal quantity;       // "quantity"
    private String quoteLineReason;
    
    @JsonProperty("QuoteLineComment") // Nom envoyé VERS Business Central
    @JsonAlias("quoteLineComment")    // Nom accepté VENANT de Postman/Front-end
    private String quoteLineComment;

    @JsonProperty("treated")
    private Boolean treated;
}
