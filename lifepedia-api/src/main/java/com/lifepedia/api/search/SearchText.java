package com.lifepedia.api.search;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Texto da busca: sem acento e em minúsculas, igual ao {@code search_norm} do banco. */
public final class SearchText {

    public static final int MAX_WORDS = 8;
    private static final int MAX_WORD = 40;
    private static final int SNIPPET = 140;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern MARKUP = Pattern.compile("[#*_>`~|\\[\\]()!]+|<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchText() {
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String plain = MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        return SPACES.matcher(plain.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    public static List<String> words(String normalized) {
        if (normalized.isEmpty()) return List.of();
        return Arrays.stream(normalized.split(" "))
                .map(w -> w.length() > MAX_WORD ? w.substring(0, MAX_WORD) : w)
                .distinct().limit(MAX_WORDS).toList();
    }

    /** Padrão de LIKE da busca numa lista (o texto inteiro, sem acento); nulo sem busca. */
    public static String listPattern(String q) {
        if (q == null || q.isBlank()) return null;
        return like(normalize(q.length() > SearchQuery.MAX_TEXT ? q.substring(0, SearchQuery.MAX_TEXT) : q));
    }

    /** Para {@code LIKE ... ESCAPE '\'}. */
    public static String like(String word) {
        return "%" + word.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    /** Trecho do texto em volta da primeira palavra achada (sem marcação); o começo quando nenhuma aparece. */
    public static String snippet(String text, List<String> words) {
        if (text == null || text.isBlank()) return null;
        String plain = SPACES.matcher(MARKUP.matcher(text).replaceAll(" ")).replaceAll(" ").trim();
        String normalized = MARKS.matcher(Normalizer.normalize(plain, Normalizer.Form.NFD)).replaceAll("").toLowerCase(Locale.ROOT);
        int at = words.stream().mapToInt(normalized::indexOf).filter(i -> i >= 0).min().orElse(0);
        if (normalized.length() != plain.length()) at = 0;
        int start = Math.max(0, at - SNIPPET / 3);
        int end = Math.min(plain.length(), start + SNIPPET);
        return (start > 0 ? "…" : "") + plain.substring(start, end).trim() + (end < plain.length() ? "…" : "");
    }
}
