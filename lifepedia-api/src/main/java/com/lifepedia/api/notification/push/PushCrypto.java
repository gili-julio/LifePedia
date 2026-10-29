package com.lifepedia.api.notification.push;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/** Criptografia do Web Push: corpo em {@code aes128gcm} (RFC 8291) e cabeçalho VAPID com JWT ES256 (RFC 8292). */
public final class PushCrypto {

    private static final int RECORD_SIZE = 4096;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64_DECODER = Base64.getUrlDecoder();
    private static final ECParameterSpec P256 = p256();

    private PushCrypto() {
    }

    /** Cifra {@code payload} para o aparelho ({@code p256dh} e {@code auth} em base64url, como o navegador entrega). */
    public static byte[] encrypt(byte[] payload, String p256dh, String auth) {
        try {
            byte[] uaPublic = decode(p256dh);
            byte[] authSecret = decode(auth);
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair server = generator.generateKeyPair();
            byte[] asPublic = uncompressed((ECPublicKey) server.getPublic());

            KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
            agreement.init(server.getPrivate());
            agreement.doPhase(publicKey(uaPublic), true);
            byte[] sharedSecret = agreement.generateSecret();

            byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic);
            byte[] ikm = hkdf(authSecret, sharedSecret, keyInfo, 32);
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            byte[] cek = hkdf(salt, ikm, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
            byte[] nonce = hkdf(salt, ikm, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
            // 0x02 = último (e único) registro.
            byte[] cipherText = cipher.doFinal(concat(payload, new byte[]{2}));

            ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
            header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
            return concat(header.array(), cipherText);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar o push.", e);
        }
    }

    /** Valor do cabeçalho {@code Authorization} para o serviço de push de {@code audience} (origem do endpoint). */
    public static String vapid(String audience, String subject, String publicKey, String privateKey, long expiresAt) {
        try {
            String header = B64.encodeToString("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
            String claims = B64.encodeToString("{\"aud\":\"%s\",\"exp\":%d,\"sub\":\"%s\"}"
                    .formatted(audience, expiresAt, subject).getBytes(StandardCharsets.UTF_8));
            Signature signer = Signature.getInstance("SHA256withECDSAinP1363Format");
            signer.initSign(privateKey(privateKey));
            signer.update((header + "." + claims).getBytes(StandardCharsets.US_ASCII));
            String jwt = header + "." + claims + "." + B64.encodeToString(signer.sign());
            return "vapid t=" + jwt + ", k=" + publicKey;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar o push.", e);
        }
    }

    /** Chave pública de aparelho válida: ponto P-256 não comprimido (65 bytes). */
    public static boolean validPublicKey(String value) {
        try {
            byte[] bytes = decode(value);
            publicKey(bytes);
            return true;
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    public static boolean validAuth(String value) {
        try {
            return decode(value).length == 16;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Confere que as duas chaves VAPID formam um par. */
    public static boolean keyPairMatches(String publicKey, String privateKey) {
        try {
            byte[] probe = "lifepedia".getBytes(StandardCharsets.US_ASCII);
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(privateKey(privateKey));
            signer.update(probe);
            byte[] signature = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey(decode(publicKey)));
            verifier.update(probe);
            return verifier.verify(signature);
        } catch (IllegalArgumentException | GeneralSecurityException e) {
            return false;
        }
    }

    static byte[] decode(String value) {
        return B64_DECODER.decode(value.replace('+', '-').replace('/', '_').replace("=", ""));
    }

    static ECPublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new IllegalArgumentException("Chave pública inválida.");
        }
        ECPoint point = new ECPoint(new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65)));
        return (ECPublicKey) KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(point, P256));
    }

    static byte[] uncompressed(ECPublicKey key) {
        return concat(new byte[]{4}, fixed(key.getW().getAffineX()), fixed(key.getW().getAffineY()));
    }

    static byte[] hkdf(byte[] salt, byte[] ikm, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        return Arrays.copyOf(mac.doFinal(concat(info, new byte[]{1})), length);
    }

    private static ECPrivateKey privateKey(String value) throws GeneralSecurityException {
        byte[] d = decode(value);
        if (d.length != 32) throw new IllegalArgumentException("Chave privada inválida.");
        return (ECPrivateKey) KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256));
    }

    private static byte[] fixed(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, out, 32 - length, length);
        return out;
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) out.writeBytes(part);
        return out.toByteArray();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
            parameters.init(new ECGenParameterSpec("secp256r1"));
            return parameters.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
