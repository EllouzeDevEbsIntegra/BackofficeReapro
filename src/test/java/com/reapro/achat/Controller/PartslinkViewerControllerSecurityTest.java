package com.reapro.achat.Controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Vérifie au niveau configuration que les endpoints globaux dangereux
 * (restart/close du pool) ne sont PAS accessibles à un utilisateur standard,
 * et que l'ancien viewer iframe a bien été supprimé.
 */
class PartslinkViewerControllerSecurityTest {

    @Test
    void restartAndCloseAreAdminOnly() throws NoSuchMethodException {
        Method restart = PartslinkViewerController.class.getDeclaredMethod("restartSession");
        Method close = PartslinkViewerController.class.getDeclaredMethod("closeSession");

        PreAuthorize restartGuard = restart.getAnnotation(PreAuthorize.class);
        PreAuthorize closeGuard = close.getAnnotation(PreAuthorize.class);

        assertThat(restartGuard).as("restart doit être protégé").isNotNull();
        assertThat(closeGuard).as("close doit être protégé").isNotNull();
        assertThat(restartGuard.value()).contains("SUPERADMIN");
        assertThat(closeGuard.value()).contains("SUPERADMIN");
    }

    @Test
    void legacyIframeViewerEndpointIsRemoved() {
        assertThatThrownBy(() ->
                PartslinkViewerController.class.getDeclaredMethod("openViewer", String.class, String.class))
                .isInstanceOf(NoSuchMethodException.class);
    }
}
