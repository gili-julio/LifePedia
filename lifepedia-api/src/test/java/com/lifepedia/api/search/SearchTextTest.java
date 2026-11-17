package com.lifepedia.api.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SearchTextTest {

    @Test
    void normalizesLikeTheDatabase() {
        assertThat(SearchText.normalize("  Pão   de AÇÚCAR ")).isEqualTo("pao de acucar");
        assertThat(SearchText.words("pao pao de acucar")).containsExactly("pao", "de", "acucar");
        assertThat(SearchText.words("")).isEmpty();
    }

    @Test
    void escapesLikeWildcards() {
        assertThat(SearchText.like("50%_off\\x")).isEqualTo("%50\\%\\_off\\\\x%");
        assertThat(SearchText.listPattern("  ")).isNull();
        assertThat(SearchText.listPattern("Preço")).isEqualTo("%preco%");
    }

    @Test
    void snippetShowsTheMatchWithoutMarkup() {
        String text = "# Pão caseiro\n" + "Uma linha longa de introdução. ".repeat(5) + "Usar **fermento** natural e esperar.";
        String snippet = SearchText.snippet(text, List.of("fermento"));
        assertThat(snippet).startsWith("…").contains("fermento natural").doesNotContain("**").doesNotContain("#");
        assertThat(SearchText.snippet("Curto", List.of("nada"))).isEqualTo("Curto");
        assertThat(SearchText.snippet(null, List.of("x"))).isNull();
    }
}
