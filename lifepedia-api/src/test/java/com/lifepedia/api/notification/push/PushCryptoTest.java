package com.lifepedia.api.notification.push;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class PushCryptoTest {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    @Test
    void encryptsForTheDeviceKeys() throws Exception {
        KeyPair device = p256();
        byte[] devicePublic = PushCrypto.uncompressed((ECPublicKey) device.getPublic());
        byte[] auth = new byte[16];
        Arrays.fill(auth, (byte) 7);
        byte[] payload = "{\"title\":\"Casa\",\"body\":\"Oi, gente!\"}".getBytes(StandardCharsets.UTF_8);

        byte[] body = PushCrypto.encrypt(payload, B64.encodeToString(devicePublic), B64.encodeToString(auth));

        assertThat(decrypt(body, (ECPrivateKey) device.getPrivate(), devicePublic, auth)).isEqualTo(payload);
        assertThat(PushCrypto.encrypt(payload, B64.encodeToString(devicePublic), B64.encodeToString(auth))).isNotEqualTo(body);
    }

    @Test
    void signsVapidTokenWithTheServerKey() throws Exception {
        KeyPair server = p256();
        String publicKey = B64.encodeToString(PushCrypto.uncompressed((ECPublicKey) server.getPublic()));
        String privateKey = B64.encodeToString(fixed(((ECPrivateKey) server.getPrivate()).getS().toByteArray()));

        String header = PushCrypto.vapid("https://fcm.googleapis.com", "mailto:a@b.dev", publicKey, privateKey, 1_900_000_000L);

        assertThat(header).startsWith("vapid t=").endsWith(", k=" + publicKey);
        String[] jwt = header.substring("vapid t=".length(), header.indexOf(',')).split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(jwt[1]), StandardCharsets.UTF_8);
        assertThat(claims).contains("\"aud\":\"https://fcm.googleapis.com\"", "\"exp\":1900000000", "\"sub\":\"mailto:a@b.dev\"");
        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(server.getPublic());
        verifier.update((jwt[0] + "." + jwt[1]).getBytes(StandardCharsets.US_ASCII));
        assertThat(verifier.verify(Base64.getUrlDecoder().decode(jwt[2]))).isTrue();
        assertThat(PushCrypto.keyPairMatches(publicKey, privateKey)).isTrue();
        assertThat(PushCrypto.keyPairMatches(B64.encodeToString(PushCrypto.uncompressed((ECPublicKey) p256().getPublic())),
                privateKey)).isFalse();
    }

    @Test
    void validatesDeviceKeys() throws Exception {
        String valid = B64.encodeToString(PushCrypto.uncompressed((ECPublicKey) p256().getPublic()));
        assertThat(PushCrypto.validPublicKey(valid)).isTrue();
        assertThat(PushCrypto.validPublicKey(B64.encodeToString(new byte[65]))).isFalse();
        assertThat(PushCrypto.validPublicKey("não é base64")).isFalse();
        assertThat(PushCrypto.validAuth(B64.encodeToString(new byte[16]))).isTrue();
        assertThat(PushCrypto.validAuth(B64.encodeToString(new byte[8]))).isFalse();
    }

    /** Lado do navegador (RFC 8291), para conferir o que a API cifra. */
    private static byte[] decrypt(byte[] body, ECPrivateKey devicePrivate, byte[] devicePublic, byte[] auth) throws Exception {
        ByteBuffer buffer = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buffer.get(salt);
        assertThat(buffer.getInt()).isEqualTo(4096);
        byte[] serverPublic = new byte[buffer.get()];
        buffer.get(serverPublic);
        byte[] cipherText = new byte[buffer.remaining()];
        buffer.get(cipherText);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(devicePrivate);
        agreement.doPhase(PushCrypto.publicKey(serverPublic), true);
        byte[] info = ByteBuffer.allocate(14 + 65 + 65).put("WebPush: info\0".getBytes(StandardCharsets.US_ASCII))
                .put(devicePublic).put(serverPublic).array();
        byte[] ikm = PushCrypto.hkdf(auth, agreement.generateSecret(), info, 32);
        byte[] cek = PushCrypto.hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = PushCrypto.hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] plain = cipher.doFinal(cipherText);
        assertThat(plain[plain.length - 1]).isEqualTo((byte) 2);
        return Arrays.copyOf(plain, plain.length - 1);
    }

    private static KeyPair p256() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static byte[] fixed(byte[] raw) {
        byte[] out = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, out, 32 - length, length);
        return out;
    }
}
