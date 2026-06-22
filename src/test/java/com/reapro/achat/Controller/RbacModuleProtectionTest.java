package com.reapro.achat.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RBAC Lot 4 — vérifie (sans contexte Spring ni DB) que les contrôleurs métier portent bien
 * le {@code @PreAuthorize} attendu avec la bonne permission. Garde-fou anti-régression : empêche
 * qu'un futur changement retire silencieusement la barrière backend.
 */
class RbacModuleProtectionTest {

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
    void classLevelGuards() {
        assertThat(classGuard(TecDocController.class)).contains("TECDOC_CATALOG_ACCESS");
        assertThat(classGuard(ArticleManagementController.class)).contains("ARTICLE_MANAGEMENT_ACCESS");
        assertThat(classGuard(PartslinkNativeController.class)).contains("PARTSLINK_ACCESS");
        assertThat(classGuard(SalesOrderController.class)).contains("B2B_ACCESS");
        assertThat(classGuard(CustomerController.class)).contains("B2B_ACCESS");
        assertThat(classGuard(CustomerFinancialController.class)).contains("B2B_ACCESS");
        assertThat(classGuard(SearchHistoryController.class)).contains("SEARCH_OPPORTUNITIES_ACCESS");
        assertThat(classGuard(SearchOpportunityController.class)).contains("SEARCH_OPPORTUNITIES_ACCESS");
        // Endpoints partagés Comparateur / Confirmation Achat → any-of.
        assertThat(classGuard(CompareQuoteController.class))
                .contains("COMPARATOR_ACCESS").contains("PURCHASE_CONFIRMATION_ACCESS");
        assertThat(classGuard(CompareQuoteLineController.class))
                .contains("COMPARATOR_ACCESS").contains("PURCHASE_CONFIRMATION_ACCESS");
        assertThat(classGuard(QuoteLineBCController.class))
                .contains("COMPARATOR_ACCESS").contains("PURCHASE_CONFIRMATION_ACCESS");
    }

    @Test
    void syncAdaptableGuards() throws NoSuchMethodException {
        assertThat(methodGuard(ReportErpSyncController.class, "getSyncData",
                String.class, int.class, int.class, String.class, String.class, String.class, String.class))
                .contains("ADAPTABLE_SYNC_ACCESS");
        assertThat(methodGuard(ReportErpSyncController.class, "triggerSync"))
                .contains("ADAPTABLE_SYNC_RUN");
        assertThat(methodGuard(ReportErpSyncController.class, "getSyncStatus"))
                .contains("ADAPTABLE_SYNC_ACCESS");
        assertThat(methodGuard(ElvaItemController.class, "syncElvaItems"))
                .contains("ADAPTABLE_SYNC_RUN");
    }

    @Test
    void purchaseCartActionGuards() throws NoSuchMethodException {
        // Écriture panier = action panier Comparateur.
        assertThat(methodGuard(PurchaseCartController.class, "addPurchaseCartLine",
                String.class, com.reapro.achat.DTO.bc.PurchaseCartLineCreateRequest.class))
                .contains("COMPARATOR_CART_ACTIONS");
        assertThat(methodGuard(PurchaseCartController.class, "updatePurchaseCartLine",
                String.class, Integer.class, com.reapro.achat.DTO.bc.PurchaseCartLineUpdateRequest.class))
                .contains("COMPARATOR_CART_ACTIONS");
    }

    @Test
    void quoteLinePatchIsActionGuarded() throws NoSuchMethodException {
        assertThat(methodGuard(QuoteLineBCController.class, "updateQuoteLine",
                String.class, String.class, com.reapro.achat.DTO.bc.QuoteLineUpdateRequest.class))
                .contains("PURCHASE_CONFIRMATION_ACTIONS").contains("COMPARATOR_CART_ACTIONS");
    }

    @Test
    void systemMaintenanceGuard() throws NoSuchMethodException {
        assertThat(methodGuard(SalesOrderSyncController.class, "resyncItems"))
                .contains("SYSTEM_SETTINGS_ACCESS");
    }
}
