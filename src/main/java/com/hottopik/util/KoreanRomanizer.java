package com.hottopik.util;

public class KoreanRomanizer {

    private static final String[] CHOSEONG = {
            "g", "kk", "n", "d", "tt", "r", "m", "b", "pp", "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    };

    private static final String[] JUNGSEONG = {
            "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i"
    };

    private static final String[] JONGSEONG = {
            "", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "p", "l", "t", "p", "l", "m", "p", "p", "t", "t", "ng", "t", "t", "k", "t", "p", "t"
    };

    public static String romanize(String korean) {
        if (korean == null || korean.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < korean.length(); i++) {
            char c = korean.charAt(i);

            if (c >= 0xAC00 && c <= 0xD7A3) {
                int unicode = c - 0xAC00;
                int choseongIndex = unicode / (21 * 28);
                int jungseongIndex = (unicode % (21 * 28)) / 28;
                int jongseongIndex = unicode % 28;

                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != ' ' && sb.charAt(sb.length() - 1) != '-') {
                    sb.append("-");
                }

                sb.append(CHOSEONG[choseongIndex]);
                sb.append(JUNGSEONG[jungseongIndex]);
                sb.append(JONGSEONG[jongseongIndex]);
            } else {
                sb.append(c);
            }
        }
        return sb.toString().trim();
    }
}
