package com.lifepedia.api.note;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoteExcerptTest {

    @Test
    void removesMarkdownSyntax() {
        String markdown = """
                ## Assunto

                Derivadas e **regra da cadeia**, ver [[Resumo]] e [site](https://x.dev).

                - [x] lista 1
                1. primeiro

                | Fórmula | Exemplo |
                | ------- | :-----: |
                | x² | 2x |

                ```js
                dobro(x)
                ```

                ---
                ![foto](/api/notes/1/images/2)
                """;
        assertThat(NoteService.excerpt(markdown))
                .isEqualTo("Assunto Derivadas e regra da cadeia, ver Resumo e site. lista 1 primeiro Fórmula Exemplo x² 2x dobro(x)");
    }

    @Test
    void cutsLongText() {
        assertThat(NoteService.excerpt("a".repeat(500))).hasSize(161).endsWith("…");
    }
}
