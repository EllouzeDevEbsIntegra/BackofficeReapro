package com.reapro.achat.DTO.tecdoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TecDocBrandsResponse {

    private BrandsData data;
    private int status;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BrandsData {
        private List<TecDocBrand> array;
    }
}