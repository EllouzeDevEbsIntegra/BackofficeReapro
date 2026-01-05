// src/main/java/com/reapro/achat/services/CompareQuoteService.java

package com.reapro.achat.services;

import com.reapro.achat.entities.sqlserver.CompareQuote;
import com.reapro.achat.repositories.sqlserver.CompareQuoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompareQuoteService {

    private final CompareQuoteRepository repository;

    public Page<CompareQuote> getAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    public Page<CompareQuote> search(String search, Pageable pageable) {
        if (search == null || search.trim().isEmpty()) {
            return repository.findAll(pageable);
        }
        return repository.search(search.trim(), pageable);
    }

    public CompareQuote getByNo(String no) {
        return repository.findById(no)
                .orElseThrow(() -> new RuntimeException("Compare Quote non trouvé : " + no));
    }
}