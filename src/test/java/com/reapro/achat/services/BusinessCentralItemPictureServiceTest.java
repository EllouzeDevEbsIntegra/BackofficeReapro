package com.reapro.achat.services;

import com.reapro.achat.DTO.bc.BcItemPicture;
import com.reapro.achat.DTO.bc.BcItemPictureResponse;
import com.reapro.achat.DTO.bc.BcPictureContent;
import com.reapro.achat.DTO.bc.BcStandardItem;
import com.reapro.achat.DTO.bc.BcStandardItemResponse;
import com.reapro.achat.exceptions.ApiException;
import com.reapro.achat.exceptions.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du service photo article BC : résolution itemNo → systemId,
 * GET (présent / absent), UPDATE (existant vs création), DELETE (présent / absent).
 * {@link BusinessCentralService} est mocké → aucune dépendance réseau / BC réelle.
 */
class BusinessCentralItemPictureServiceTest {

    private static final String COMPANY = "COMP-1";
    private static final String ITEM_NO = "317542";
    private static final String ITEM_ID = "11111111-1111-1111-1111-111111111111";
    private static final String PIC_ID  = "72b3a2ec-8953-ed11-8a25-00155d1bd903";

    private BusinessCentralService bc;
    private BusinessCentralItemPictureService service;

    @BeforeEach
    void setUp() {
        bc = mock(BusinessCentralService.class);
        service = new BusinessCentralItemPictureService(bc);
    }

    // ── Helpers ────────────────────────────────────────────────
    private BcStandardItemResponse itemFound() {
        BcStandardItem it = new BcStandardItem();
        it.setId(ITEM_ID);
        it.setNumber(ITEM_NO);
        BcStandardItemResponse resp = new BcStandardItemResponse();
        resp.setValue(List.of(it));
        return resp;
    }

    private BcItemPictureResponse pictureFound() {
        BcItemPicture pic = new BcItemPicture();
        pic.setId(PIC_ID);
        pic.setContentType("image/png");
        pic.setDynamic("content@odata.mediaReadLink", "http://host/.../picture(" + PIC_ID + ")/content");
        BcItemPictureResponse resp = new BcItemPictureResponse();
        resp.setValue(List.of(pic));
        return resp;
    }

    private BcItemPictureResponse pictureEmpty() {
        BcItemPictureResponse resp = new BcItemPictureResponse();
        resp.setValue(List.of());
        return resp;
    }

    private void stubItemResolved() {
        when(bc.getStandard(eq("items"), eq(COMPANY), any(), eq(BcStandardItemResponse.class)))
                .thenReturn(itemFound());
    }

    // ── resolveItemId ──────────────────────────────────────────
    @Test
    void resolveItemId_returnsSystemId() {
        stubItemResolved();
        assertThat(service.resolveItemId(COMPANY, ITEM_NO)).isEqualTo(ITEM_ID);
    }

    @Test
    void resolveItemId_throwsWhenItemMissing() {
        when(bc.getStandard(eq("items"), eq(COMPANY), any(), eq(BcStandardItemResponse.class)))
                .thenReturn(new BcStandardItemResponse()); // value == null

        assertThatThrownBy(() -> service.resolveItemId(COMPANY, ITEM_NO))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_NOT_FOUND);
    }

    // ── getPicture ─────────────────────────────────────────────
    @Test
    void getPicture_returnsNullWhenNoPicture() {
        stubItemResolved();
        when(bc.getStandard(eq("items(" + ITEM_ID + ")/picture"), eq(COMPANY), any(), eq(BcItemPictureResponse.class)))
                .thenReturn(pictureEmpty());

        assertThat(service.getPicture(COMPANY, ITEM_NO)).isNull();
        verify(bc, never()).getStandardBinary(any(), any());
    }

    @Test
    void getPicture_returnsBinaryAndContentType() {
        stubItemResolved();
        when(bc.getStandard(eq("items(" + ITEM_ID + ")/picture"), eq(COMPANY), any(), eq(BcItemPictureResponse.class)))
                .thenReturn(pictureFound());
        byte[] data = {1, 2, 3, 4};
        when(bc.getStandardBinary(eq("items(" + ITEM_ID + ")/picture(" + PIC_ID + ")/content"), eq(COMPANY)))
                .thenReturn(data);

        BcPictureContent result = service.getPicture(COMPANY, ITEM_NO);

        assertThat(result).isNotNull();
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.content()).containsExactly(1, 2, 3, 4);
    }

    // ── updatePicture ──────────────────────────────────────────
    @Test
    void updatePicture_existingPicture_patchesContentEditLink_withWildcardEtag() {
        stubItemResolved();
        when(bc.getStandard(eq("items(" + ITEM_ID + ")/picture"), eq(COMPANY), any(), eq(BcItemPictureResponse.class)))
                .thenReturn(pictureFound());

        service.updatePicture(COMPANY, ITEM_NO, new byte[]{9}, "image/jpeg");

        ArgumentCaptor<String> endpoint = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ifMatch = ArgumentCaptor.forClass(String.class);
        verify(bc).patchStandardBinary(endpoint.capture(), eq(COMPANY), any(), eq("image/jpeg"), ifMatch.capture());
        assertThat(endpoint.getValue()).isEqualTo("items(" + ITEM_ID + ")/picture(" + PIC_ID + ")/content");
        assertThat(ifMatch.getValue()).isEqualTo("*");
    }

    @Test
    void updatePicture_noExistingPicture_usesCreationEndpoint() {
        stubItemResolved();
        when(bc.getStandard(eq("items(" + ITEM_ID + ")/picture"), eq(COMPANY), any(), eq(BcItemPictureResponse.class)))
                .thenReturn(pictureEmpty());

        service.updatePicture(COMPANY, ITEM_NO, new byte[]{9}, "image/png");

        ArgumentCaptor<String> endpoint = ArgumentCaptor.forClass(String.class);
        verify(bc).patchStandardBinary(endpoint.capture(), eq(COMPANY), any(), eq("image/png"), eq("*"));
        assertThat(endpoint.getValue()).isEqualTo("items(" + ITEM_ID + ")/picture/pictureContent");
    }

    // ── deletePicture ──────────────────────────────────────────
    @Test
    void deletePicture_present_deletesWithWildcardEtag() {
        stubItemResolved();
        when(bc.getStandard(eq("items(" + ITEM_ID + ")/picture"), eq(COMPANY), any(), eq(BcItemPictureResponse.class)))
                .thenReturn(pictureFound());

        service.deletePicture(COMPANY, ITEM_NO);

        verify(bc).deleteStandard(eq("items(" + ITEM_ID + ")/picture(" + PIC_ID + ")"), eq(COMPANY), eq("*"));
    }

    @Test
    void deletePicture_absent_throwsPictureNotFound() {
        stubItemResolved();
        when(bc.getStandard(eq("items(" + ITEM_ID + ")/picture"), eq(COMPANY), any(), eq(BcItemPictureResponse.class)))
                .thenReturn(pictureEmpty());

        assertThatThrownBy(() -> service.deletePicture(COMPANY, ITEM_NO))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.PICTURE_NOT_FOUND);
        verify(bc, never()).deleteStandard(any(), any(), any());
    }
}
