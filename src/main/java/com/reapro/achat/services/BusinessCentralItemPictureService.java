package com.reapro.achat.services;

import com.reapro.achat.DTO.bc.BcItemPicture;
import com.reapro.achat.DTO.bc.BcItemPictureResponse;
import com.reapro.achat.DTO.bc.BcPictureContent;
import com.reapro.achat.DTO.bc.BcStandardItem;
import com.reapro.achat.DTO.bc.BcStandardItemResponse;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gestion de la photo d'un article (item) Business Central.
 *
 * Réutilise {@link BusinessCentralService} (config/host/companyId/token issus des paramètres BC —
 * jamais en dur ici). Aucune URL BC ni en-tête d'authentification n'est exposé au frontend.
 *
 * Endpoints BC standard utilisés (API beta configurée) :
 *  - GET    items({itemId})/picture                       → métadonnées (contentType + lien média)
 *  - GET    items({itemId})/picture({pictureId})/content  → binaire image
 *  - PATCH  items({itemId})/picture/pictureContent        → création/MAJ du contenu (doc Microsoft)
 *  - DELETE items({itemId})/picture({pictureId})          → suppression
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusinessCentralItemPictureService {

    private final BusinessCentralService bcService;

    /** L'API BC exige un If-Match ; "*" = quel que soit l'ETag courant. */
    private static final String ANY_ETAG = "*";

    /**
     * Résout l'itemNo (référence article Reapro) vers le systemId (GUID) de l'item BC,
     * via l'API STANDARD {@code items} (même surface d'API que l'endpoint {@code picture},
     * ce qui garantit que l'id renvoyé est valide pour la photo).
     *
     * @throws ApiException 404 si l'article est introuvable.
     */
    public String resolveItemId(String companyId, String itemNo) {
        if (itemNo == null || itemNo.isBlank()) {
            throw new ApiException(ErrorCode.ITEM_NOT_FOUND, "Référence article manquante");
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("$filter", "number eq '" + escapeOData(itemNo) + "'");
        params.put("$select", "id,number");

        BcStandardItemResponse resp =
                bcService.getStandard("items", companyId, params, BcStandardItemResponse.class);

        List<BcStandardItem> items = (resp != null && resp.getValue() != null) ? resp.getValue() : List.of();
        if (items.isEmpty() || items.get(0).getId() == null || items.get(0).getId().isBlank()) {
            throw new ApiException(ErrorCode.ITEM_NOT_FOUND,
                    "Article introuvable dans Business Central : " + itemNo);
        }
        return items.get(0).getId();
    }

    /**
     * GET : binaire de la photo + content-type, ou {@code null} si l'article n'a pas de photo.
     */
    public BcPictureContent getPicture(String companyId, String itemNo) {
        String itemId = resolveItemId(companyId, itemNo);

        BcItemPicture pic = findPicture(companyId, itemId);
        if (pic == null) {
            return null; // pas de photo → 404/204 géré par le contrôleur
        }

        byte[] bytes = bcService.getStandardBinary(
                "items(" + itemId + ")/picture(" + pic.getId() + ")/content", companyId);
        if (bytes == null || bytes.length == 0) {
            return null;
        }

        String type = (pic.getContentType() != null && !pic.getContentType().isBlank())
                ? pic.getContentType()
                : "image/jpeg";
        return new BcPictureContent(bytes, type);
    }

    /**
     * UPDATE : crée ou remplace la photo de l'item BC à partir du binaire fourni.
     */
    public void updatePicture(String companyId, String itemNo, byte[] content, String contentType) {
        String itemId = resolveItemId(companyId, itemNo);
        BcItemPicture existing = findPicture(companyId, itemId);

        // Photo existante → on PATCH le lien d'édition du média (picture({id})/content).
        // Sinon → endpoint de création documenté par Microsoft (picture/pictureContent).
        String endpoint = (existing != null)
                ? "items(" + itemId + ")/picture(" + existing.getId() + ")/content"
                : "items(" + itemId + ")/picture/pictureContent";

        bcService.patchStandardBinary(endpoint, companyId, content, contentType, ANY_ETAG);
    }

    /**
     * DELETE : supprime la photo de l'item BC.
     *
     * @throws ApiException 404 si l'article n'a pas de photo.
     */
    public void deletePicture(String companyId, String itemNo) {
        String itemId = resolveItemId(companyId, itemNo);
        BcItemPicture existing = findPicture(companyId, itemId);
        if (existing == null) {
            throw new ApiException(ErrorCode.PICTURE_NOT_FOUND,
                    "Aucune photo à supprimer pour l'article : " + itemNo);
        }
        bcService.deleteStandard("items(" + itemId + ")/picture(" + existing.getId() + ")", companyId, ANY_ETAG);
    }

    // ─────────────────────────────────────────────────────────────

    /** Métadonnées de la photo (id + contentType + lien média), ou null si absente. */
    private BcItemPicture findPicture(String companyId, String itemId) {
        BcItemPictureResponse resp = bcService.getStandard(
                "items(" + itemId + ")/picture", companyId, null, BcItemPictureResponse.class);

        if (resp == null || resp.getValue() == null || resp.getValue().isEmpty()) {
            return null;
        }
        BcItemPicture pic = resp.getValue().get(0);
        return (pic != null && pic.hasContent()) ? pic : null;
    }

    private String escapeOData(String s) {
        return s == null ? "" : s.replace("'", "''");
    }
}
