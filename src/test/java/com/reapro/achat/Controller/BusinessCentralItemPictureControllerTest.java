package com.reapro.achat.Controller;

import com.reapro.achat.DTO.PictureDeleteResponse;
import com.reapro.achat.DTO.PictureUpdateResponse;
import com.reapro.achat.DTO.bc.BcPictureContent;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import com.reapro.achat.services.BusinessCentralItemPictureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du contrôleur photo article BC : validation du fichier (UPDATE),
 * statut 404 sur GET sans photo, et délégation correcte au service. Service mocké.
 */
class BusinessCentralItemPictureControllerTest {

    private static final String COMPANY = "COMP-1";
    private static final String ITEM_NO = "317542";

    private BusinessCentralItemPictureService service;
    private BusinessCentralItemPictureController controller;

    @BeforeEach
    void setUp() {
        service = mock(BusinessCentralItemPictureService.class);
        controller = new BusinessCentralItemPictureController(service);
    }

    private MockMultipartFile file(String type, byte[] content) {
        return new MockMultipartFile("file", "photo.jpg", type, content);
    }

    // ── GET ────────────────────────────────────────────────────
    @Test
    void getPicture_returns404WhenNoPicture() {
        when(service.getPicture(COMPANY, ITEM_NO)).thenReturn(null);

        ResponseEntity<byte[]> resp = controller.getPicture(ITEM_NO, COMPANY);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getPicture_returnsImageWithContentType() {
        when(service.getPicture(COMPANY, ITEM_NO))
                .thenReturn(new BcPictureContent(new byte[]{1, 2, 3}, "image/png"));

        ResponseEntity<byte[]> resp = controller.getPicture(ITEM_NO, COMPANY);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(resp.getBody()).containsExactly(1, 2, 3);
    }

    // ── UPDATE : validation ────────────────────────────────────
    @Test
    void updatePicture_rejectsEmptyFile() {
        assertThatThrownBy(() -> controller.updatePicture(ITEM_NO, file("image/jpeg", new byte[0]), COMPANY))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_FILE);
        verify(service, never()).updatePicture(any(), any(), any(), any());
    }

    @Test
    void updatePicture_rejectsUnsupportedType() {
        assertThatThrownBy(() -> controller.updatePicture(ITEM_NO, file("application/pdf", new byte[]{1, 2}), COMPANY))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_IMAGE_FILE);
        verify(service, never()).updatePicture(any(), any(), any(), any());
    }

    @Test
    void updatePicture_acceptsValidImageAndDelegates() {
        PictureUpdateResponse resp =
                controller.updatePicture(ITEM_NO, file("image/jpeg", new byte[]{1, 2, 3}), COMPANY);

        assertThat(resp.getItemNo()).isEqualTo(ITEM_NO);
        assertThat(resp.isUpdated()).isTrue();
        assertThat(resp.getContentType()).isEqualTo("image/jpeg");
        verify(service).updatePicture(eq(COMPANY), eq(ITEM_NO), any(), eq("image/jpeg"));
    }

    // ── DELETE ─────────────────────────────────────────────────
    @Test
    void deletePicture_delegatesAndReturnsFlag() {
        PictureDeleteResponse resp = controller.deletePicture(ITEM_NO, COMPANY);

        assertThat(resp.getItemNo()).isEqualTo(ITEM_NO);
        assertThat(resp.isDeleted()).isTrue();
        verify(service).deletePicture(COMPANY, ITEM_NO);
    }
}
