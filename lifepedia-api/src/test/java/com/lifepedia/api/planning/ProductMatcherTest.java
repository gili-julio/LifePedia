package com.lifepedia.api.planning;

import com.lifepedia.api.planning.ProductMatcher.Candidate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMatcherTest {

    private static Candidate product(String name, int stores) {
        return new Candidate(name, BigDecimal.TEN, null, "/p", false, stores, null);
    }

    private static Candidate offer(String name) {
        return new Candidate(name, BigDecimal.TEN, null, null, true, 0, "Loja");
    }

    @Test
    void queriesDropNoiseAndJoinUnits() {
        assertThat(ProductMatcher.queries("Apple iPhone 15 128 GB Preto"))
                .containsExactly("apple iphone 15 128gb");
        assertThat(ProductMatcher.queries("Fone de ouvido on ear sem fio JBL Tune 520BT preto"))
                .containsExactly("fone ouvido on ear fio jbl tune 520bt", "jbl tune 520bt");
    }

    @Test
    void modelNumbersMustMatch() {
        assertThat(ProductMatcher.best("Tênis Nike Revolution 7 masculino", List.of(
                product("Tênis Nike Masculino Revolution 6 Casual", 1),
                product("Tênis Nike Masculino Revolution 7", 1)))).extracting(Candidate::name)
                .isEqualTo("Tênis Nike Masculino Revolution 7");
        assertThat(ProductMatcher.best("Geladeira 375 litros", List.of(product("Geladeira Brastemp 375L Inox", 3))))
                .isNotNull();
        assertThat(ProductMatcher.best("Cadeira gamer TGC12", List.of(product("Cadeira gamer TGC15", 3)))).isNull();
    }

    @Test
    void skipsAccessoriesUsedAndUnaskedVariants() {
        Candidate best = ProductMatcher.best("Apple iPhone 15 128gb", List.of(
                product("Celular Apple iPhone 15 128GB 6GB Usado", 3),
                product("iPhone 15 Plus 128GB", 1),
                product("iPhone 15 128GB 6GB", 7)));
        assertThat(best.name()).isEqualTo("iPhone 15 128GB 6GB");
        assertThat(ProductMatcher.best("JBL Tune 520BT", List.of(
                offer("Capa rígida compatível com fone JBL Tune 520BT")))).isNull();
    }

    @Test
    void prefersProductPageOnNearTie() {
        Candidate best = ProductMatcher.best("Air fryer Mondial AFN-40", List.of(
                offer("Air Fryer Mondial AFN-40 4L"),
                product("Fritadeira Air Fryer Mondial AFN-40 4L", 6)));
        assertThat(best.offer()).isFalse();
    }

    @Test
    void unrelatedNamesDoNotMatch() {
        assertThat(ProductMatcher.best("Kit 10 pares meia soquete masculina", List.of(
                product("Kit C/10 Pares Meias Invisível Soquete Lupo", 1)))).isNotNull();
        assertThat(ProductMatcher.best("Geladeira frost free", List.of(product("Fogão 4 bocas", 2)))).isNull();
        assertThat(ProductMatcher.best("   ", List.of(product("Fogão", 2)))).isNull();
    }
}
