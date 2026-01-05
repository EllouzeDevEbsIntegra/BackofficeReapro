package com.reapro.achat.DTO.bc;

import lombok.Data;

/**
 * Requête de notre API /api/bc/items/copy
 * Les champs correspondent à ceux attendus par l'API BC ItemCopy,
 * sauf "category" qui sera toujours fixé à "PR" dans le service.
 */
@Data
public class ItemCopyRequest {

    private String ref;         // Référence article BC à créer
    private String frs;         // Vendor No
    private String refTecdoc;   // Référence TecDoc
    private String refMaster;   // ReferenceMaster
    private String group;       // Group
    private String subGroup;    // SubGroup
    private String champsLibre; // ChampsLibre
    private String manufacturer;// Manufacturer (code FABxxxx)
    private String marque;      // Marque (texte)
}