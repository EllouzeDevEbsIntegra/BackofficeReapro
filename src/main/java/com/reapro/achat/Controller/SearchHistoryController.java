package com.reapro.achat.Controller;

import com.reapro.achat.DTO.SearchHistoryResponse;
import com.reapro.achat.entities.primary.LocalSearchHistory;
import com.reapro.achat.repositories.primary.LocalSearchHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search-history")
@RequiredArgsConstructor
public class SearchHistoryController {

    private final LocalSearchHistoryRepository localSearchHistoryRepository;

    @GetMapping
    public Page<SearchHistoryResponse> getSearchHistory(Pageable pageable) {
        return localSearchHistoryRepository.findAll(pageable)
                .map(this::mapToDto);
    }

    private SearchHistoryResponse mapToDto(LocalSearchHistory entity) {
        return SearchHistoryResponse.builder()
                .id(entity.getId())
                .filterDecoded(entity.getFilterDecoded())
                .creationDate(entity.getCreationDate())
                .type(entity.getType())
                .customerExtId(entity.getCustomerExtId())
                .companyName(entity.getCompanyName())
                .resultsCount(entity.getResultsCount())
                .isStockAvailable(entity.getIsStockAvailable()) // NOUVEAU CHAMP
                .syncedAt(entity.getSyncedAt())
                .build();
    }
}
