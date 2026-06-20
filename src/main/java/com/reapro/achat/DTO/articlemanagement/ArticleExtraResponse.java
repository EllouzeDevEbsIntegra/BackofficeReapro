package com.reapro.achat.DTO.articlemanagement;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Données « lourdes » de l'article chargées à la demande (dialog détail), hors de la liste :
 * dernier achat (vue SQL Server) — pour ne pas ralentir la liste.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticleExtraResponse {
    private String itemNo;
    private LocalDate lastPurchaseDate;
}
