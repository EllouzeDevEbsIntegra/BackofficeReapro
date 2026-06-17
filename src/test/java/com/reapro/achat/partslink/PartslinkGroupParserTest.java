package com.reapro.achat.partslink;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests du parser de groupes multi-marques, basés sur les dumps DOM réels debug/live.
 */
class PartslinkGroupParserTest {

    private static final char NBSP = (char) 0x00A0;

    @Test
    void bmw_idValuePlusDescription() {
        // BMW : idValue=88, descriptionValue=libellé
        PartslinkGroupParser.ParsedGroup g = PartslinkGroupParser.parseRow(
                "88", "Pièces&Packs éco. Service et réparation", "", 0);
        assertThat(g).isNotNull();
        assertThat(g.code()).isEqualTo("88");
        assertThat(g.label()).isEqualTo("Pièces&Packs éco. Service et réparation");
    }

    @Test
    void audiVw_captionValueCodeAndLabelInSameSpan() {
        // Audi/VW : pas d'idValue, caption="1<nbsp><nbsp>Moteur" -> code=1, label=Moteur
        String caption = "1" + NBSP + NBSP + "Moteur";
        PartslinkGroupParser.ParsedGroup g = PartslinkGroupParser.parseRow("", "", caption, 0);
        assertThat(g).isNotNull();
        assertThat(g.code()).isEqualTo("1");
        assertThat(g.label()).isEqualTo("Moteur");
    }

    @Test
    void mercedes_captionWithoutLeadingCode_usesPositionalCode() {
        // Mercedes : idValue inutilisable, caption sans code numérique -> code positionnel "#1"
        PartslinkGroupParser.ParsedGroup g = PartslinkGroupParser.parseRow(
                "", "", "Les meilleures pages image", 0);
        assertThat(g).isNotNull();
        assertThat(g.code()).isEqualTo("#1");
        assertThat(g.label()).isEqualTo("Les meilleures pages image");
    }

    @Test
    void mercedes_dashOnlyIdValue_isIgnoredAsCode() {
        // idValue "- -" (tirets/espaces) ne doit jamais servir de code
        assertThat(PartslinkGroupParser.isUsableCode("- -")).isFalse();
        assertThat(PartslinkGroupParser.isUsableCode("-")).isFalse();
        assertThat(PartslinkGroupParser.isUsableCode("  ")).isFalse();
        assertThat(PartslinkGroupParser.isUsableCode("88")).isTrue();

        PartslinkGroupParser.ParsedGroup g = PartslinkGroupParser.parseRow(
                "- -", "", "7" + NBSP + "Transmission", 3);
        assertThat(g.code()).isEqualTo("7"); // depuis le caption, pas le "- -"
        assertThat(g.label()).isEqualTo("Transmission");
    }

    @Test
    void captionWord_isNotMistakenForCode() {
        // Un mot sans chiffre ("Les") ne doit pas être pris pour un code
        assertThat(PartslinkGroupParser.parseCaptionCode("Les meilleures pages")).isNull();
        assertThat(PartslinkGroupParser.parseCaptionCode("10 Boîte")[0]).isEqualTo("10");
    }

    @Test
    void rowWithoutAnyLabel_isSkipped() {
        assertThat(PartslinkGroupParser.parseRow("", "", "", 0)).isNull();
        assertThat(PartslinkGroupParser.parseRow(null, null, null, 0)).isNull();
    }

    @Test
    void positionalCodeIsStablePerIndex() {
        assertThat(PartslinkGroupParser.parseRow("", "", "Divers", 0).code()).isEqualTo("#1");
        assertThat(PartslinkGroupParser.parseRow("", "", "Divers", 4).code()).isEqualTo("#5");
    }
}
