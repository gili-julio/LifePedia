package com.lifepedia.api.planning;

import com.lifepedia.api.planning.ProductMatcher.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceReferenceServiceTest {

    @Test
    void parsesBuscapeSearchData() {
        List<Candidate> list = PriceReferenceService.parse("""
                <html><body><script id="__NEXT_DATA__" type="application/json">
                {"props":{"initialReduxState":{"hits":{"hits":[
                 {"type":"product","name":"Headphone JBL Tune 520BT","price":180.41,"storeCount":7,
                  "image":"https://i.zst.com.br/thumbs/45/23/1/-1.jpg","url":"/fone/jbl-tune-520bt",
                  "bestOffer":{"merchantName":"Magazine Luiza"}},
                 {"type":"offer","name":"Fone JBL Tune 520BT Branco","price":"251.01","url":"/lead?oid=1"},
                 {"type":"product","name":"Sem preço"},
                 {"type":"product","name":"Link externo","price":10,"url":"//evil.com/x"}
                ]}}}}</script></body></html>""");
        assertThat(list).hasSize(3);
        Candidate first = list.getFirst();
        assertThat(first.offer()).isFalse();
        assertThat(first.price()).isEqualByComparingTo("180.41");
        assertThat(first.stores()).isEqualTo(7);
        assertThat(first.store()).isEqualTo("Magazine Luiza");
        assertThat(first.path()).isEqualTo("/fone/jbl-tune-520bt");
        assertThat(list.get(1).offer()).isTrue();
        assertThat(list.get(2).path()).isNull();
    }

    @Test
    void pageWithoutDataGivesNothing() {
        assertThat(PriceReferenceService.parse("<html><title>Just a moment...</title></html>")).isEmpty();
        assertThat(PriceReferenceService.parse("<script id=\"__NEXT_DATA__\">{quebrado</script>")).isEmpty();
    }
}
