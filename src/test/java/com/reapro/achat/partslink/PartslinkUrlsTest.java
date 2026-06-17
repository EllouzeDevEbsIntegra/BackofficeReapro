package com.reapro.achat.partslink;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Garantit qu'aucune query string (token/session potentiel) ne fuit dans les dumps/logs. */
class PartslinkUrlsTest {

    @Test
    void stripsQueryString() {
        assertThat(PartslinkUrls.stripQuery(
                "https://www.partslink24.com/pl24-app/mercedes_parts/VIN/0/vehicle?desktop=true&lang=fr&sid=SECRET"))
                .isEqualTo("https://www.partslink24.com/pl24-app/mercedes_parts/VIN/0/vehicle");
    }

    @Test
    void stripsFragment() {
        assertThat(PartslinkUrls.stripQuery("https://x/y#frag")).isEqualTo("https://x/y");
    }

    @Test
    void leavesPlainUrlUntouched_andHandlesNull() {
        assertThat(PartslinkUrls.stripQuery("https://x/y")).isEqualTo("https://x/y");
        assertThat(PartslinkUrls.stripQuery(null)).isNull();
    }
}
