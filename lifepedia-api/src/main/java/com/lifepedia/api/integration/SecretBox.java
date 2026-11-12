package com.lifepedia.api.integration;

import com.lifepedia.api.config.IntegrationProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/** Cifra tokens de contas externas com AES-256-GCM ({@code INTEGRATIONS_KEY}); o banco só guarda "v1:" + base64(iv + cifra). */
@Component
public class SecretBox {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public SecretBox(IntegrationProperties properties) {
        if (!properties.tokensEnabled()) {
            this.key = null;
            return;
        }
        byte[] raw = Base64.getDecoder().decode(properties.key().trim());
        if (raw.length != 32) throw new IllegalStateException("INTEGRATIONS_KEY precisa ter 32 bytes em base64.");
        this.key = new SecretKeySpec(raw, "AES");
    }

    public boolean enabled() {
        return key != null;
    }

    public String seal(String plain) {
        if (plain == null) return null;
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + sealed.length).put(iv).put(sealed).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar.", e);
        }
    }

    /** Abre o que {@link #seal} gerou; nulo se vier nulo, adulterado ou de outra chave. */
    public String open(String sealed) {
        if (sealed == null || !sealed.startsWith(PREFIX) || key == null) return null;
        try {
            byte[] all = Base64.getDecoder().decode(sealed.substring(PREFIX.length()));
            if (all.length <= IV_BYTES) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    private void requireKey() {
        if (key == null) throw new IllegalStateException("INTEGRATIONS_KEY não configurada.");
    }
}
