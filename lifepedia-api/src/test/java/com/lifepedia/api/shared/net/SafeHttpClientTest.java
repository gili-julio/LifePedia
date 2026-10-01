package com.lifepedia.api.shared.net;

import com.lifepedia.api.shared.net.SafeHttpClient.FetchException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Proteção contra SSRF: nada de rede interna, esquemas ou portas fora do padrão. */
class SafeHttpClientTest {

    private static SafeHttpClient client;

    @BeforeAll
    static void setUp() {
        client = new SafeHttpClient();
    }

    @AfterAll
    static void tearDown() throws Exception {
        client.destroy();
    }

    @Test
    void onlyPublicAddresses() throws UnknownHostException {
        for (String ip : new String[]{"127.0.0.1", "10.1.2.3", "172.20.0.5", "192.168.0.1", "169.254.169.254",
                "100.64.0.1", "0.0.0.0", "224.0.0.1", "::1", "fd00::1", "fe80::1", "::ffff:127.0.0.1",
                "64:ff9b::a00:1", "2002:c0a8:0101::1"}) {
            assertThat(SafeHttpClient.isPublic(InetAddress.getByName(ip))).as(ip).isFalse();
        }
        assertThat(SafeHttpClient.isPublic(InetAddress.getByName("8.8.8.8"))).isTrue();
        assertThat(SafeHttpClient.isPublic(InetAddress.getByName("2606:4700:4700::1111"))).isTrue();
    }

    @Test
    void userUrlRules() {
        assertThat(SafeHttpClient.parseUserUrl(" Loja.com.br/p?x=1&y=%26 ").toString())
                .isEqualTo("https://loja.com.br/p?x=1&y=%26");
        for (String bad : new String[]{"javascript:alert(1)", "ftp://loja.com/x", "http://loja.com:8080/",
                "http://user:senha@loja.com/", "file:///etc/passwd", ""}) {
            assertThatThrownBy(() -> SafeHttpClient.parseUserUrl(bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void refusesInternalHosts() {
        for (String url : new String[]{"http://127.0.0.1/", "http://localhost/", "http://[::1]/", "http://169.254.169.254/latest"}) {
            assertThatThrownBy(() -> client.get(URI.create(url), "*/*", 1024, t -> true))
                    .as(url).isInstanceOf(FetchException.class);
        }
    }
}
