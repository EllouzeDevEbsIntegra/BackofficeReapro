package com.reapro.achat.services;

import com.reapro.achat.DTO.bc.ItemCopyBCBody;
import com.reapro.achat.DTO.bc.ItemCopyBCResponse;
import com.reapro.achat.DTO.bc.ItemCopyRequest;
import com.reapro.achat.DTO.bc.ItemCopyResultResponse;
import com.reapro.achat.entities.primary.Admin;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import com.reapro.achat.repositories.primary.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;   // ✅ import

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemCopyService {

    private final BusinessCentralService bcService;
    private final AdminRepository adminRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper; // ✅

    /**
     * Crée un article dans BC via l'API ItemCopy.
     * Utilise la société BC liée à l'utilisateur connecté.
     */
    public ItemCopyResultResponse copyItem(String userEmail, ItemCopyRequest req) {

        // 1. Récupérer l'admin + companyId
        Admin admin = adminRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Utilisateur introuvable."));

        if (admin.getBcCompanyId() == null || admin.getBcCompanyId().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Aucune société BC n'est affectée à votre profil. Veuillez choisir une société."
            );
        }
        String companyId = admin.getBcCompanyId().trim();

        // 2. (Optionnel) quelques validations simples
        if (req.getRef() == null || req.getRef().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le champ 'ref' est obligatoire.");
        }
        if (req.getFrs() == null || req.getFrs().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le champ 'frs' est obligatoire.");
        }

        // 3. Construire le body pour BC (category toujours "PR")
        ItemCopyBCBody body = ItemCopyBCBody.builder()
                .ref(req.getRef())
                .frs(req.getFrs())
                .refTecdoc(req.getRefTecdoc())
                .refMaster(req.getRefMaster())
                .category("PR")                // 🔒 FIXE
                .group(req.getGroup())
                .subGroup(req.getSubGroup())
                .champsLibre(req.getChampsLibre())
                .manufacturer(req.getManufacturer())
                .marque(req.getMarque())
                .build();

        try {
            String json = objectMapper.writeValueAsString(body);
            log.info("ItemCopy - JSON envoyé à BC : {}", json);
        } catch (Exception e) {
            log.warn("Impossible de sérialiser ItemCopyBCBody pour les logs", e);
        }

        // 4. Appel BC : POST companies(ID)/ItemCopy
        ItemCopyBCResponse bcResp = bcService.postCustom(
                "ItemCopy",
                companyId,
                body,
                ItemCopyBCResponse.class
        );

        if (bcResp == null || bcResp.getRef() == null || bcResp.getRef().isBlank()) {
            throw new ApiException(
                    ErrorCode.BC_API_ERROR,
                    "Réponse invalide de Business Central lors de la création d'article."
            );
        }

        // 5. Message final pour le front
        String msg = bcResp.getRef() + " créé avec succès !";
        return new ItemCopyResultResponse(msg);
    }
}