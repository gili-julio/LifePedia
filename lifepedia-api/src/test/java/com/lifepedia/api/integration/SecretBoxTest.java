package com.lifepedia.api.integration;

import com.lifepedia.api.config.IntegrationProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tokens cifrados: nunca em texto puro, adulteração ou outra chave não abrem. */
class SecretBoxTest {

    private static final String KEY = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes());
    private static final String OTHER = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes());

    @Test
    void sealsAndOpens() {
        SecretBox box = new SecretBox(new IntegrationProperties(KEY, null, null));
        String sealed = box.seal("glpat-segredo");
        assertThat(sealed).startsWith("v1:").doesNotContain("segredo");
        assertThat(box.seal("glpat-segredo")).isNotEqualTo(sealed);
        assertThat(box.open(sealed)).isEqualTo("glpat-segredo");
    }

    @Test
    void refusesTamperedOrForeign() {
        SecretBox box = new SecretBox(new IntegrationProperties(KEY, null, null));
        String sealed = box.seal("token");
        char last = sealed.charAt(sealed.length() - 2);
        String tampered = sealed.substring(0, sealed.length() - 2) + (last == 'A' ? 'B' : 'A') + sealed.charAt(sealed.length() - 1);
        assertThat(box.open(tampered)).isNull();
        assertThat(new SecretBox(new IntegrationProperties(OTHER, null, null)).open(sealed)).isNull();
        assertThat(box.open("token")).isNull();
    }

    @Test
    void withoutKey() {
        SecretBox box = new SecretBox(new IntegrationProperties("", null, null));
        assertThat(box.enabled()).isFalse();
        assertThat(box.open("v1:abc")).isNull();
        assertThatThrownBy(() -> box.seal("x")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SecretBox(new IntegrationProperties("c2hvcnQ=", null, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
