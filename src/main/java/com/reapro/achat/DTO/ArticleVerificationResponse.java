package com.reapro.achat.DTO;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class ArticleVerificationResponse {
    private int totalTecDocItems;      // Total renvoyé par TecDoc
    private int countEligible;         // Ceux dont le fabricant existe chez nous
    private int countCreated;          // Ceux qui sont déjà créés dans BC
    private int countNotCreated;       // Ceux qui manquent
    private List<VerificationItem> items;

    @Data
    @Builder
    public static class VerificationItem {
        private Long dataSupplierId;
        private String manufacturerName;
        private String articleNumber;      // Référence TecDoc
        private String status;             // "CREATED" ou "NOT_CREATED"
        private String bcItemNo;           // Le No_ BC si trouvé
        private String articleDescription; // Description TecDoc (optionnel)
        private String bcManufacturerCode; // ex: FAB0001
        private String bcManufacturerName; // ex: ORIGINE - MERCEDES/SMART
        private String vendorNo;
        private String referenceMaster;
    }
}