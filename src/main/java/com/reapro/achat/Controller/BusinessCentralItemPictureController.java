package com.reapro.achat.Controller;

import com.reapro.achat.DTO.PictureDeleteResponse;
import com.reapro.achat.DTO.PictureUpdateResponse;
import com.reapro.achat.DTO.bc.BcPictureContent;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import com.reapro.achat.services.BusinessCentralItemPictureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

/**
 * Photo article Business Central.
 *
 * Le frontend n'appelle QUE ces endpoints Reapro (jamais BC directement) :
 *  - GET    /api/bc/items/{itemNo}/picture → binaire image (ou 404)
 *  - PUT    /api/bc/items/{itemNo}/picture → upload multipart (champ "file")
 *  - DELETE /api/bc/items/{itemNo}/picture → suppression
 *
 * Sécurité : routes authentifiées (règle globale {@code /api/bc/**} → {@code .authenticated()},
 * identique aux autres modifications d'article BC ; aucun rôle dédié n'existe aujourd'hui).
 * Aucune URL interne BC ni en-tête/credential BC n'est renvoyé au client.
 */
@RestController
@RequestMapping("/api/bc/items")
@RequiredArgsConstructor
@Slf4j
public class BusinessCentralItemPictureController {

    private final BusinessCentralItemPictureService pictureService;

    // companyId par défaut : même convention que les autres contrôleurs BC Reapro.
    private static final String DEFAULT_COMPANY_ID = "20C5337E-2E49-EC11-A103-00155DB6A301";
    private static final long MAX_SIZE = 5L * 1024 * 1024; // 5 Mo
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    /** GET binaire image, ou 404 si pas de photo. */
    @GetMapping("/{itemNo}/picture")
    public ResponseEntity<byte[]> getPicture(
            @PathVariable String itemNo,
            @RequestParam(defaultValue = DEFAULT_COMPANY_ID) String companyId) {

        BcPictureContent pic = pictureService.getPicture(companyId, itemNo);
        if (pic == null) {
            return ResponseEntity.notFound().build();
        }

        MediaType type;
        try {
            type = MediaType.parseMediaType(pic.contentType());
        } catch (Exception e) {
            type = MediaType.IMAGE_JPEG;
        }
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.noCache())
                .body(pic.content());
    }

    /** UPDATE/upload : multipart/form-data, champ "file" (jpeg/png/webp, max 5 Mo). */
    @PutMapping(value = "/{itemNo}/picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PictureUpdateResponse updatePicture(
            @PathVariable String itemNo,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = DEFAULT_COMPANY_ID) String companyId) {

        if (file == null || file.isEmpty()) {
            throw new ApiException(ErrorCode.INVALID_IMAGE_FILE, "Fichier image vide ou manquant");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new ApiException(ErrorCode.INVALID_IMAGE_FILE, "Fichier trop volumineux (max 5 Mo)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(ErrorCode.INVALID_IMAGE_FILE,
                    "Format non supporté — formats acceptés : JPEG, PNG, WEBP");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new ApiException(ErrorCode.INVALID_IMAGE_FILE, "Lecture du fichier impossible");
        }

        pictureService.updatePicture(companyId, itemNo, bytes, contentType);
        return PictureUpdateResponse.builder()
                .itemNo(itemNo)
                .updated(true)
                .contentType(contentType)
                .build();
    }

    /** DELETE photo. */
    @DeleteMapping("/{itemNo}/picture")
    public PictureDeleteResponse deletePicture(
            @PathVariable String itemNo,
            @RequestParam(defaultValue = DEFAULT_COMPANY_ID) String companyId) {

        pictureService.deletePicture(companyId, itemNo);
        return PictureDeleteResponse.builder()
                .itemNo(itemNo)
                .deleted(true)
                .build();
    }
}
