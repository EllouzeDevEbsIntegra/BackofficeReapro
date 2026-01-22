package com.reapro.achat.services;

import com.reapro.achat.DTO.CompareQuoteLineResponse;
import com.reapro.achat.entities.sqlserver.CompareQuoteLine;
import com.reapro.achat.repositories.sqlserver.CompareQuoteLineRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CompareQuoteLineService {

    private final CompareQuoteLineRepository repository;

    public Page<CompareQuoteLineResponse> getLinesFiltered(
            String userEmail,
            String compareQuoteNo,
            String search,
            String itemNo,
            Integer pageNumber,
            Boolean isTreated,
            Pageable pageable
    ) {
        // 1. Base de la Spécification
        Specification<CompareQuoteLine> spec = Specification.where(equals("compareQuoteNo", compareQuoteNo));

        // 2. Ajout des filtres dynamiques
        spec = spec.and(searchOr(search))
                .and(like("itemNo", itemNo))
                .and(equals("pageNumber", pageNumber));

        // 3. Filtre sur "treated" (basé sur nbLineNotThreated)
        if (isTreated != null) {
            if (isTreated) {
                spec = spec.and((root, query, cb) -> cb.equal(root.get("nbLineNotThreated"), 0));
            } else {
                spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("nbLineNotThreated"), 0));
            }
        }

        // 4. Exécution de la requête
        Page<CompareQuoteLine> page = repository.findAll(spec, pageable);

        // 5. Mapping vers DTO
        return page.map(line -> {
            boolean treatedStatus = (line.getNbLineNotThreated() == null || line.getNbLineNotThreated() == 0);

            return new CompareQuoteLineResponse(
                    line.getSystemId(),
                    line.getCompareQuoteNo(),
                    line.getItemNo(),
                    line.getCreationDate(),
                    line.getPageNumber(),
                    line.getStructuredDescription(),
                    // countItemManual is removed from the response
                    line.getNbLineNotThreated(),
                    treatedStatus,
                    line.getItemProductCode(),
                    line.getItemSubProductCode(),
                    line.getGroupe(),
                    line.getSousGroupe(),
                    line.getChampsLibre(),
                    line.getProduit(),
                    line.getMakeCode()
            );
        });
    }

    // ... (Specification utility methods remain the same)
    private static Specification<CompareQuoteLine> equals(String field, Object value) {
        return (root, query, cb) -> value == null ? cb.conjunction() : cb.equal(root.get(field), value);
    }

    private static Specification<CompareQuoteLine> like(String field, String value) {
        return (root, query, cb) ->
                value == null || value.isBlank()
                        ? cb.conjunction()
                        : cb.like(cb.lower(root.get(field)), "%" + value.trim().toLowerCase() + "%");
    }

    private static Specification<CompareQuoteLine> searchOr(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) return cb.conjunction();
            String p = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("compareQuoteNo")), p),
                    cb.like(cb.lower(root.get("itemNo")), p),
                    cb.like(cb.lower(root.get("structuredDescription")), p)
            );
        };
    }
}
