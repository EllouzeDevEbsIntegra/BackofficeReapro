package com.reapro.achat.Controller;

import com.reapro.achat.DTO.bc.ItemCopyRequest;
import com.reapro.achat.DTO.bc.ItemCopyResultResponse;
import com.reapro.achat.services.ItemCopyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/bc/items")
@RequiredArgsConstructor
public class ItemCopyController {

    private final ItemCopyService itemCopyService;

    /**
     * Création d'un article via BC ItemCopy
     *
     * POST /api/bc/items/copy
     * Body JSON :
     * {
     *   "ref": "22-0693-0",
     *   "frs": "401280",
     *   "refTecdoc": "22-0693-0",
     *   "refMaster": "MASTERLR019618",
     *   "group": "FAM1163",
     *   "subGroup": "SF001340",
     *   "champsLibre": "TEST CREATE WS",
     *   "manufacturer": "FAB0074",
     *   "marque": "LAND ROVER"
     * }
     *
     * Réponse JSON :
     * { "ref": "TEST123456 créé avec succès !" }
     */
    @PostMapping("/copy")
    public ItemCopyResultResponse copyItem(
            @AuthenticationPrincipal String email,
            @RequestBody ItemCopyRequest request
    ) {
        return itemCopyService.copyItem(email, request);
    }
}