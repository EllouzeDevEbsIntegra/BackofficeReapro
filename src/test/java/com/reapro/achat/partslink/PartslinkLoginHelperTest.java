package com.reapro.achat.partslink;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Garantit qu'aucun secret (username/password) ne fuite dans les messages d'erreur / logs. */
class PartslinkLoginHelperTest {

    private PartslinkLoginHelper helper(String user, String pwd) {
        PartslinkProperties p = new PartslinkProperties();
        p.setUsername(user);
        p.setPassword(pwd);
        return new PartslinkLoginHelper(p);
    }

    @Test
    void sanitizeRedactsCredentials() {
        PartslinkLoginHelper helper = helper("admin", "SECRET_PWD_123");
        String redacted = helper.sanitize("login failed user=admin password=SECRET_PWD_123");
        assertThat(redacted).doesNotContain("SECRET_PWD_123");
        assertThat(redacted).doesNotContain("admin");
        assertThat(redacted).contains("****");
    }

    @Test
    void sanitizeHandlesNull() {
        assertThat(helper("admin", "x").sanitize(null)).isEqualTo("Erreur inconnue");
    }
}
