package com.lifepedia.api.search;

import com.lifepedia.api.finance.EntryType;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Um resultado da busca geral.
 *
 * @param link    rota da web que abre o item
 * @param snippet trecho do texto longo onde a busca achou (nota, mensagem)
 * @param fields  detalhes para a prévia, já em texto
 */
public record SearchHit(SearchKind kind, UUID id, String title, String subtitle, String snippet, String link,
                        LocalDate date, BigDecimal amount, EntryType entryType, String groupName, List<Field> fields,
                        double score) {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM/yyyy");

    public record Field(String label, String value) {
    }

    public static Builder of(SearchKind kind, UUID id, String title, String link, double score) {
        return new Builder(kind, id, title, link, score);
    }

    public static String day(LocalDate date) {
        return date == null ? null : DAY.format(date);
    }

    public static String month(LocalDate date) {
        return date == null ? null : MONTH.format(date);
    }

    /** Valor de parâmetro de rota ({@code ?busca=...}). */
    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static final class Builder {

        private final SearchKind kind;
        private final UUID id;
        private final String title;
        private final String link;
        private final double score;
        private final List<Field> fields = new ArrayList<>();
        private String subtitle;
        private String snippet;
        private LocalDate date;
        private BigDecimal amount;
        private EntryType entryType;
        private String groupName;

        private Builder(SearchKind kind, UUID id, String title, String link, double score) {
            this.kind = kind;
            this.id = id;
            this.title = title;
            this.link = link;
            this.score = score;
        }

        public Builder subtitle(String value) {
            subtitle = value;
            return this;
        }

        public Builder snippet(String value) {
            snippet = value;
            return this;
        }

        public Builder date(LocalDate value) {
            date = value;
            return this;
        }

        public Builder amount(BigDecimal value, EntryType type) {
            amount = value;
            entryType = type;
            return this;
        }

        public Builder group(String name) {
            groupName = name;
            return this;
        }

        /** Ignora valor vazio. */
        public Builder field(String label, String value) {
            if (value != null && !value.isBlank()) fields.add(new Field(label, value));
            return this;
        }

        public SearchHit build() {
            return new SearchHit(kind, id, title, subtitle, snippet, link, date, amount, entryType, groupName,
                    List.copyOf(fields), score);
        }
    }
}
