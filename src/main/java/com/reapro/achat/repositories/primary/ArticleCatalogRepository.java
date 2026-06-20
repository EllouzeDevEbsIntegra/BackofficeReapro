package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.ElvaItemCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Accès au CATALOGUE article via le cache PostgreSQL {@code elva_item_cache}
 * (synchronisé périodiquement depuis SQL Server par {@code ElvaItemService}).
 *
 * Page « Gestion Articles » : liste/recherche/pagination/count rapides sur le cache
 * (les champs temps réel — stock/qté import/prix — sont récupérés séparément côté SQL Server).
 * Repository dédié → n'altère pas {@code ElvaItemCacheRepository} utilisé par B2B.
 */
@Repository
public interface ArticleCatalogRepository
        extends JpaRepository<ElvaItemCache, String>, JpaSpecificationExecutor<ElvaItemCache> {

    /**
     * Comptage OEM groupé, calculé EN POSTGRES depuis le cache (rapide, pas de scan de vue SQL Server).
     * Même sémantique que l'endpoint unitaire : équivalences OEM (is_oem='1') liées via reference_origine_lie,
     * dédupliquées sur REPLACE(no,'MASTER',''). Requête native (REPLACE/COUNT standard Postgres).
     */
    @Query(value = "SELECT reference_origine_lie AS refKey, COUNT(DISTINCT REPLACE(no, 'MASTER', '')) AS cnt "
            + "FROM elva_item_cache "
            + "WHERE is_oem = '1' AND reference_origine_lie IN (:keys) "
            + "GROUP BY reference_origine_lie", nativeQuery = true)
    List<OemCountProjection> findOemCountsByKeys(@Param("keys") Collection<String> keys);

    interface OemCountProjection {
        String getRefKey();
        long getCnt();
    }
}
