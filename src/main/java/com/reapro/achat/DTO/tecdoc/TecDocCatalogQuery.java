package com.reapro.achat.DTO.tecdoc;

/**
 * Paramètres de la recherche catalogue TecDoc (page « Catalogue TecDoc »).
 * Tous les champs sont optionnels sauf la pagination ({@code page} / {@code perPage}).
 *
 * <p>Le pays ({@code articleCountry} = "TN"), la langue ({@code lang} = "fr") et le
 * {@code provider} sont résolus côté serveur dans {@code TecDocService} — ils ne
 * transitent jamais par le frontend.</p>
 */
public record TecDocCatalogQuery(
        String searchQuery,
        Integer searchType,
        String searchMatchType,
        Long assemblyGroupNodeId,
        Long linkageTargetId,
        String linkageTargetType,
        Integer dataSupplierIds,
        int page,
        int perPage
) {}
