package com.lifepedia.api.planning;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** Compara nomes de produto: tira palavras vazias e cores, junta "128 gb" e mede o quanto o candidato cobre a busca. */
final class ProductMatcher {

    /** Candidato achado na busca; {@code offer} = oferta avulsa de uma loja (não é página de produto). */
    record Candidate(String name, BigDecimal price, String imageUrl, String path, boolean offer, int stores,
                     String store) {
    }

    static final double MIN_SCORE = 0.6;
    private static final double TIE = 0.05;
    private static final int MAX_QUERY_WORDS = 8;

    private static final Set<String> STOPWORDS = Set.of("de", "da", "do", "das", "dos", "com", "sem", "para", "pra",
            "e", "o", "a", "os", "as", "em", "no", "na", "nos", "nas", "por", "c", "um", "uma", "the", "and", "for",
            "with");
    private static final Set<String> NOISE = Set.of("preto", "preta", "branco", "branca", "azul", "vermelho",
            "vermelha", "verde", "rosa", "cinza", "prata", "dourado", "dourada", "grafite", "bege", "marrom", "amarelo",
            "amarela", "roxo", "roxa", "lilas", "laranja", "cor", "original", "novo", "nova", "lacrado", "lacrada",
            "envio", "imediato", "frete", "gratis", "promocao", "oferta", "lancamento", "polegadas", "pol");
    private static final Set<String> UNITS = Set.of("gb", "tb", "mb", "l", "ml", "kg", "g", "w", "v", "mah", "hz");
    /** Candidato com isso (e a busca sem) é outro produto: acessório ou usado. */
    private static final Set<String> EXCLUDED = Set.of("capa", "capinha", "case", "pelicula", "suporte", "compativel",
            "usado", "usada", "seminovo", "seminova", "recondicionado", "recondicionada", "vitrine", "refil");
    /** Variante que a busca não pediu (ex.: "iPhone 15 Plus" para "iPhone 15"). */
    private static final Set<String> VARIANTS = Set.of("plus", "pro", "max", "mini", "ultra", "lite", "fe", "slim", "neo");

    private ProductMatcher() {
    }

    /** Buscas a tentar, da mais completa à mais curta. */
    static List<String> queries(String name) {
        List<String> words = words(name);
        if (words.isEmpty()) return List.of();
        List<String> full = words.subList(0, Math.min(words.size(), MAX_QUERY_WORDS));
        Set<String> result = new LinkedHashSet<>();
        result.add(String.join(" ", full));
        if (full.size() > 3) {
            // Modelo e o que vem antes (marca/linha); sem números, as primeiras palavras.
            Set<Integer> keep = new TreeSet<>();
            for (int i = 0; i < full.size(); i++) {
                if (hasDigit(full.get(i))) for (int j = Math.max(0, i - 2); j <= i; j++) keep.add(j);
            }
            List<String> shorter = new ArrayList<>();
            if (keep.isEmpty()) shorter.addAll(full.subList(0, 4));
            else keep.forEach(i -> shorter.add(full.get(i)));
            result.add(String.join(" ", shorter));
        }
        return List.copyOf(result);
    }

    /** Melhor candidato parecido com o nome, ou nulo. Empate: página de produto com mais lojas. */
    static Candidate best(String name, List<Candidate> candidates) {
        List<String> query = tokens(name);
        if (query.isEmpty()) return null;
        record Scored(Candidate candidate, double score, int index) {
        }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            double score = score(query, candidates.get(i).name());
            if (score >= MIN_SCORE) scored.add(new Scored(candidates.get(i), score, i));
        }
        if (scored.isEmpty()) return null;
        double top = scored.stream().mapToDouble(Scored::score).max().orElseThrow();
        return scored.stream()
                .filter(s -> s.score() >= top - TIE)
                .min(Comparator.comparing((Scored s) -> s.candidate().offer())
                        .thenComparing(s -> -s.candidate().stores())
                        .thenComparing(s -> -s.score())
                        .thenComparingInt(Scored::index))
                .map(Scored::candidate)
                .orElse(null);
    }

    /** 0 a 1; números e modelos da busca precisam aparecer no candidato. */
    static double score(List<String> query, String candidateName) {
        Set<String> candidate = new LinkedHashSet<>(tokens(candidateName));
        if (candidate.isEmpty()) return 0;
        for (String word : candidate) {
            if (EXCLUDED.contains(word) && !query.contains(word)) return 0;
        }
        double total = 0;
        double found = 0;
        int matched = 0;
        for (String word : query) {
            double weight = hasDigit(word) ? 2 : 1;
            total += weight;
            if (candidate.contains(word)) {
                found += weight;
                matched++;
            } else if (hasDigit(word)) {
                return 0;
            }
        }
        double recall = found / total;
        if (recall < MIN_SCORE) return 0;
        double precision = (double) matched / candidate.size();
        boolean variant = candidate.stream().anyMatch(w -> VARIANTS.contains(w) && !query.contains(w));
        return 0.75 * recall + 0.25 * precision - (variant ? 0.3 : 0);
    }

    /** Palavras para comparar (sem plural simples). */
    static List<String> tokens(String name) {
        List<String> result = new ArrayList<>();
        for (String word : words(name)) {
            String stem = !hasDigit(word) && word.length() > 3 && word.endsWith("s") ? word.substring(0, word.length() - 1) : word;
            if (!result.contains(stem)) result.add(stem);
        }
        return result;
    }

    /** Palavras úteis, sem acento, em minúsculas, com número e unidade juntos. */
    private static List<String> words(String name) {
        if (name == null) return List.of();
        String plain = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        String[] raw = plain.split("[^a-z0-9]+");
        List<String> result = new ArrayList<>();
        for (int i = 0; i < raw.length; i++) {
            String word = raw[i].equals("litros") || raw[i].equals("litro") ? "l" : raw[i];
            if (word.isEmpty()) continue;
            if (UNITS.contains(word) && !result.isEmpty() && result.getLast().matches("\\d+")) {
                result.set(result.size() - 1, result.getLast() + word);
                continue;
            }
            if (!STOPWORDS.contains(word) && !NOISE.contains(word)) result.add(word);
        }
        return result;
    }

    private static boolean hasDigit(String word) {
        return word.chars().anyMatch(Character::isDigit);
    }
}
