package com.reapro.achat.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PagedResponse<T> {
    private List<T> content;
    private int page;
    private int size;

    // si BC renvoie @odata.count on le met, sinon -1
    private long totalElements;
    private int totalPages;
}