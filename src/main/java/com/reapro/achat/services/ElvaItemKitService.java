package com.reapro.achat.services;

import com.reapro.achat.DTO.ElvaItemKitResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ElvaItemKitService {

    private final NamedParameterJdbcTemplate sqlServerJdbcTemplate;

    // Cache la structure du kit (composition) car elle change rarement.
    // Le nom du cache "kitsStructure" doit être configuré dans CacheConfig si vous voulez un TTL spécifique (ex: 1h ou 1 jour).
    @Cacheable(value = "kitsStructure", key = "#article")
    public List<ElvaItemKitResponse> getByArticle(String article) {
        if (article == null || article.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le paramètre 'article' est obligatoire.");
        }

        String sql = """
            SELECT
                [Article]                AS article,
                [Description]            AS description,
                [Item Kit]               AS itemKit,
                [Description Item Kit]   AS descriptionItemKit,
                [Kit]                    AS kit
            FROM [Amiral_LS].[dbo].[ELVA_ITEM_KIT]
            WHERE [Article] = :article OR [Item Kit] = :article
            """;

        log.info("SQL KIT (Cache Miss) : {}", sql);

        return sqlServerJdbcTemplate.query(
                sql,
                Map.of("article", article.trim()),
                (rs, rowNum) -> new ElvaItemKitResponse(
                        rs.getString("article"),
                        rs.getString("description"),
                        rs.getString("itemKit"),
                        rs.getString("descriptionItemKit"),
                        rs.getString("kit"),
                        null, // existPurchaseCart (sera rempli plus tard par ItemsKitService)
                        null  // commentPurchaseCart (sera rempli plus tard par ItemsKitService)
                )
        );
    }
}
