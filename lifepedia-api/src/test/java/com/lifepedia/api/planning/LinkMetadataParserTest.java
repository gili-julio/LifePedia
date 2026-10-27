package com.lifepedia.api.planning;

import com.lifepedia.api.planning.LinkMetadataParser.Metadata;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class LinkMetadataParserTest {

    private static Metadata parse(String html, String url) {
        return LinkMetadataParser.parse(Jsoup.parse(html, url), URI.create(url));
    }

    @Test
    void jsonLdProduct() {
        Metadata m = parse("""
                <html><head><title>Placa | KaBuM!</title>
                <script type="application/ld+json">{"@context":"https://schema.org","@graph":[{"@type":"WebPage"},
                {"@type":"Product","name":"Placa de Vídeo X","image":["https://img.kabum.com.br/p.jpg"],
                 "offers":{"@type":"Offer","price":"1999.90","priceCurrency":"BRL"}}]}</script>
                </head></html>""", "https://www.kabum.com.br/produto/1/placa");
        assertThat(m.title()).isEqualTo("Placa de Vídeo X");
        assertThat(m.price()).isEqualByComparingTo("1999.90");
        assertThat(m.currency()).isEqualTo("BRL");
        assertThat(m.imageUrl()).isEqualTo("https://img.kabum.com.br/p.jpg");
        assertThat(m.site()).isEqualTo("KaBuM!");
        assertThat(m.structured()).isTrue();
    }

    @Test
    void amazonPage() {
        Metadata m = parse("""
                <html><head><title>Apple iPhone 15 (128 GB) — Preto | Amazon.com.br</title></head><body>
                <span id="productTitle">  Apple iPhone 15 (128 GB) — Preto  </span>
                <img id="landingImage" data-old-hires="https://m.media-amazon.com/images/I/x.jpg">
                <script>var data = {"priceAmount":4776.77};</script></body></html>""",
                "https://www.amazon.com.br/dp/B0CP6CVJSG");
        assertThat(m.title()).isEqualTo("Apple iPhone 15 (128 GB) — Preto");
        assertThat(m.price()).isEqualByComparingTo("4776.77");
        assertThat(m.imageUrl()).isEqualTo("https://m.media-amazon.com/images/I/x.jpg");
        assertThat(m.site()).isEqualTo("Amazon");
    }

    @Test
    void openGraphWithRelativeImage() {
        Metadata m = parse("""
                <meta property="og:title" content="Tênis Corrida">
                <meta property="og:image" content="/img/t.png">
                <meta property="product:price:amount" content="299,90">
                <meta property="og:site_name" content="Loja Boa">""", "https://loja.com.br/p/tenis");
        assertThat(m.title()).isEqualTo("Tênis Corrida");
        assertThat(m.imageUrl()).isEqualTo("https://loja.com.br/img/t.png");
        assertThat(m.price()).isEqualByComparingTo("299.90");
        assertThat(m.site()).isEqualTo("Loja Boa");
    }

    @Test
    void blockedPageHasNoStructuredData() {
        Metadata m = parse("<title>Mercado Libre</title><img src=\"javascript:alert(1)\">",
                "https://www.mercadolivre.com.br/x/p/MLB1");
        assertThat(m.structured()).isFalse();
        assertThat(m.imageUrl()).isNull();
    }

    @Test
    void titleFromUrl() {
        assertThat(LinkMetadataParser.titleFromUrl(URI.create(
                "https://www.mercadolivre.com.br/apple-iphone-15-128-gb-preto/p/MLB1027172669")))
                .isEqualTo("Apple iphone 15 128 gb preto");
        assertThat(LinkMetadataParser.titleFromUrl(URI.create(
                "https://shopee.com.br/Apple-iPhone-15-i.1117433916.22184433411"))).isEqualTo("Apple iPhone 15");
        assertThat(LinkMetadataParser.titleFromUrl(URI.create(
                "https://produto.mercadolivre.com.br/MLB-3846010385-fone-bluetooth-xyz-_JM"))).isEqualTo("Fone bluetooth xyz");
        assertThat(LinkMetadataParser.titleFromUrl(URI.create("https://loja.com/123456"))).isNull();
    }

    @Test
    void prices() {
        assertThat(LinkMetadataParser.parsePrice("R$ 4.776,77")).isEqualByComparingTo("4776.77");
        assertThat(LinkMetadataParser.parsePrice("1,234.56")).isEqualByComparingTo("1234.56");
        assertThat(LinkMetadataParser.parsePrice("1.500")).isEqualByComparingTo("1500");
        assertThat(LinkMetadataParser.parsePrice("4510.89")).isEqualByComparingTo("4510.89");
        assertThat(LinkMetadataParser.parsePrice("grátis")).isNull();
        assertThat(LinkMetadataParser.parsePrice("0")).isNull();
    }

    @Test
    void titleFromVerificationRedirect() {
        assertThat(LinkMetadataParser.titleFromRedirect(URI.create("https://www.mercadolivre.com.br/gz/account-verification"
                + "?go=https%3A%2F%2Fwww.mercadolivre.com.br%2Ffone-jbl-tune-520bt%2Fp%2FMLB26296412&tid=1")))
                .isEqualTo("Fone jbl tune 520bt");
        assertThat(LinkMetadataParser.titleFromRedirect(URI.create("https://loja.com/gz/account-verification?tid=1"))).isNull();
        assertThat(LinkMetadataParser.titleFromRedirect(URI.create("https://loja.com/sofa-3-lugares-cinza")))
                .isEqualTo("Sofa 3 lugares cinza");
    }

    @Test
    void siteNameKnowsSubdomains() {
        assertThat(LinkMetadataParser.siteName(URI.create("https://produto.mercadolivre.com.br/MLB-1"))).isEqualTo("Mercado Livre");
        assertThat(LinkMetadataParser.siteName(URI.create("https://www.lojinha.com.br/x"))).isEqualTo("lojinha.com.br");
    }
}
