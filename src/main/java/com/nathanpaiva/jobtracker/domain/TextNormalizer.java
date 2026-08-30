package com.nathanpaiva.jobtracker.domain;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Puts text into the one shape every keyword comparison in this project expects:
 * lower case, without accents.
 *
 * <p>These emails arrive mostly in pt-BR, where "Seleção", "selecao" and "SELEÇÃO" are
 * the same word to a reader and three different strings to a computer. Normalising once
 * means each keyword is written once.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * NFD splits an accented character into the letter plus a separate combining mark,
     * and {@code \p{M}} then removes those marks.
     *
     * <p>{@code Locale.ROOT} is not decoration: under a Turkish locale, lower-casing "I"
     * produces a dotless "ı", and every keyword containing an i would stop matching.
     */
    public static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
