package com.reapro.achat.partslink;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de ligne de groupe principal Partslink — PUR et testable (aucune dépendance Selenium).
 *
 * <p>Fondé sur les dumps DOM réels {@code debug/live} par marque :</p>
 * <ul>
 *   <li><b>BMW</b> : idValue=code (ex. "88") + descriptionValue=libellé.</li>
 *   <li><b>Audi/VW</b> : pas d'idValue ; captionValue="1{nbsp}{nbsp}Moteur"
 *       (code + libellé dans le même span, séparés par espaces/nbsp).</li>
 *   <li><b>Mercedes</b> : idValue vide ou "- -" (inutilisable) ; captionValue=libellé
 *       sans code numérique en tête (ex. "Les meilleures pages image").</li>
 * </ul>
 *
 * <p>Choix du code, par priorité : (1) idValue s'il est exploitable ; (2) token de tête du caption
 * s'il ressemble à un code (doit contenir un chiffre) ; (3) code positionnel stable "#"+(index+1)
 * en dernier recours. Jamais de code dérivé d'un mot de libellé.</p>
 */
public final class PartslinkGroupParser {

    private static final char NBSP = (char) 0x00A0;
    private static final Pattern CAPTION_CODE = Pattern.compile("^([0-9A-Za-z._/-]{1,8})\\s+(.+)$");

    private PartslinkGroupParser() {
    }

    public record ParsedGroup(String code, String label) {
    }

    /**
     * @return groupe exploitable, ou {@code null} si la ligne n'a pas de libellé (ligne non-donnée).
     */
    public static ParsedGroup parseRow(String idValue, String descriptionValue, String captionValue, int index) {
        String label = clean(descriptionValue);
        if (!hasText(label)) {
            label = clean(captionValue);
        }
        if (!hasText(label)) {
            return null;
        }

        if (isUsableCode(idValue)) {
            return new ParsedGroup(idValue.trim(), label);
        }

        String[] fromCaption = parseCaptionCode(clean(captionValue));
        if (fromCaption != null) {
            return new ParsedGroup(fromCaption[0], fromCaption[1]);
        }
        // Aucun code fiable -> code positionnel stable (réutilisable pour re-cliquer la même ligne).
        return new ParsedGroup("#" + (index + 1), label);
    }

    /** Un idValue est exploitable s'il reste quelque chose après retrait des tirets/espaces/nbsp. */
    static boolean isUsableCode(String s) {
        if (s == null) {
            return false;
        }
        return !s.replace(NBSP, ' ').replaceAll("[-\\s]", "").isEmpty();
    }

    /** Token de tête + reste, uniquement si le token ressemble à un code (contient un chiffre). */
    static String[] parseCaptionCode(String caption) {
        if (!hasText(caption)) {
            return null;
        }
        Matcher m = CAPTION_CODE.matcher(caption);
        if (m.matches()) {
            String token = m.group(1);
            if (token.matches(".*\\d.*")) { // évite de prendre un mot (ex. "Les") pour un code
                return new String[]{token, m.group(2).trim()};
            }
        }
        return null;
    }

    /** Normalise nbsp + espaces multiples. */
    static String clean(String s) {
        if (s == null) {
            return null;
        }
        return s.replace(NBSP, ' ').replaceAll("\\s+", " ").trim();
    }

    private static boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }
}
