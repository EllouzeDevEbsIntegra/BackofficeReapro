package com.reapro.achat.Controller;

import com.reapro.achat.enums.PermissionCode;
import com.reapro.achat.services.CompanyScopeService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC Lot 4bis-B — garde-fous anti-régression sur la protection des endpoints « Info Article »
 * transversaux et de la photo article (lecture vs écriture).
 */
class RbacArticleInfoProtectionTest {

    private static final String INFO_READ = "ARTICLE_INFO_READ";

    private String classGuard(Class<?> c) {
        PreAuthorize a = c.getAnnotation(PreAuthorize.class);
        return a != null ? a.value() : "";
    }

    private String methodGuard(Class<?> c, String method, Class<?>... params) throws NoSuchMethodException {
        Method m = c.getDeclaredMethod(method, params);
        PreAuthorize a = m.getAnnotation(PreAuthorize.class);
        return a != null ? a.value() : "";
    }

    @Test
    void infoArticleReadEndpoints_requireInfoReadOrConsumerModule() {
        // Chaque contrôleur transversal doit autoriser au moins ARTICLE_INFO_READ (any-of avec les modules).
        for (Class<?> c : new Class<?>[]{
                OemCountController.class,
                LastInvoicedItemCostController.class,
                LastInvoicedItemCostHistoryController.class,
                PurchasePriceController.class,
                PurchaseLineSqlServerController.class,
                ItemsKitController.class,
                ElvaItemKitController.class,
                IntercompanyStockController.class,
                ItemLedgerEntryBCController.class,
                ImportLedgerEntryBCController.class,
                BcItemBCController.class,
                ElvaItemController.class
        }) {
            assertThat(classGuard(c))
                    .as("%s doit exiger ARTICLE_INFO_READ (any-of)", c.getSimpleName())
                    .contains(INFO_READ)
                    .contains("hasAnyAuthority");
        }
    }

    @Test
    void elvaSync_keepsAdaptableSyncRun() throws NoSuchMethodException {
        // L'annotation de méthode prime : le sync reste réservé à ADAPTABLE_SYNC_RUN.
        assertThat(methodGuard(ElvaItemController.class, "syncElvaItems"))
                .contains("ADAPTABLE_SYNC_RUN");
    }

    @Test
    void pictureGet_isReadGuarded() throws NoSuchMethodException {
        assertThat(methodGuard(BusinessCentralItemPictureController.class, "getPicture",
                String.class, String.class))
                .contains(INFO_READ).contains("hasAnyAuthority");
    }

    @Test
    void pictureWrite_requiresArticlePhotoManage() throws NoSuchMethodException {
        assertThat(methodGuard(BusinessCentralItemPictureController.class, "updatePicture",
                String.class, String.class, MultipartFile.class))
                .contains("ARTICLE_PHOTO_MANAGE");
        assertThat(methodGuard(BusinessCentralItemPictureController.class, "deletePicture",
                String.class, String.class))
                .contains("ARTICLE_PHOTO_MANAGE");
    }

    @Test
    void pictureController_resolvesCompanyFromProfile() {
        // Preuve que la société vient du profil (CompanyScopeService) et non d'un companyId client.
        boolean usesScopeService = Arrays.stream(BusinessCentralItemPictureController.class.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(t -> t.equals(CompanyScopeService.class));
        assertThat(usesScopeService)
                .as("le contrôleur photo doit résoudre la société via CompanyScopeService")
                .isTrue();
    }

    @Test
    void articlePhotoManage_isDeclaredAndActiveInRegistry() {
        PermissionCode code = PermissionCode.valueOf("ARTICLE_PHOTO_MANAGE");
        assertThat(code.isActiveByDefault()).isTrue();
        assertThat(code.getGroup()).isEqualTo("Articles / TecDoc");
    }
}
