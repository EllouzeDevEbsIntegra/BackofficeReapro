package com.reapro.achat.services;

import com.reapro.achat.entities.primary.Exclusion;
import com.reapro.achat.repositories.primary.ExclusionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExclusionService {

    private final ExclusionRepository exclusionRepository;

    @Cacheable(value = "exclusionList")
    @Transactional(readOnly = true)
    public Set<String> getExclusionList() {
        log.info("Chargement de la liste d'exclusion depuis la base de données.");
        return exclusionRepository.findAllNormalizedFilters();
    }

    @CacheEvict(value = "exclusionList", allEntries = true)
    @Transactional
    public Exclusion addExclusion(String normalizedFilter, String reason, String createdBy) {
        if (exclusionRepository.existsByNormalizedFilter(normalizedFilter)) {
            log.warn("Le filtre '{}' est déjà dans la liste d'exclusion.", normalizedFilter);
            return exclusionRepository.findByNormalizedFilter(normalizedFilter).orElse(null); // Retourne l'existant
        }
        Exclusion exclusion = Exclusion.builder()
                .normalizedFilter(normalizedFilter)
                .reason(reason)
                .createdBy(createdBy)
                .build();
        log.info("Ajout du filtre '{}' à la liste d'exclusion.", normalizedFilter);
        return exclusionRepository.save(exclusion);
    }

    @CacheEvict(value = "exclusionList", allEntries = true)
    @Transactional
    public void removeExclusion(Long id) {
        exclusionRepository.deleteById(id);
        log.info("Exclusion ID {} supprimée.", id);
    }

    @CacheEvict(value = "exclusionList", allEntries = true)
    @Transactional
    public void removeExclusionByFilter(String normalizedFilter) {
        exclusionRepository.findByNormalizedFilter(normalizedFilter).ifPresent(exclusionRepository::delete);
        log.info("Exclusion pour le filtre '{}' supprimée.", normalizedFilter);
    }

    @Transactional(readOnly = true)
    public List<Exclusion> getAllExclusions() {
        return exclusionRepository.findAll();
    }
}
