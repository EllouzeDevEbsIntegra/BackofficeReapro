package com.reapro.achat.DTO.tecdoc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TecDocApiResponse {
    private int totalMatchingArticles;
    private int maxAllowedPage;
    private int status;
    private List<TecDocArticle> articles;
}