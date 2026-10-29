package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.notification.push.PushGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PushIntegrationTest extends ApiTestSupport {

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final KeyPair SERVER = p256();

    @MockitoBean
    PushGateway gateway;

    @DynamicPropertySource
    static void vapid(DynamicPropertyRegistry registry) {
        registry.add("lifepedia.push.public-key", () -> B64.encodeToString(uncompressed((ECPublicKey) SERVER.getPublic())));
        registry.add("lifepedia.push.private-key", () -> B64.encodeToString(fixed(((ECPrivateKey) SERVER.getPrivate()).getS())));
        registry.add("lifepedia.push.subject", () -> "mailto:teste@lifepedia.dev");
    }

    @BeforeEach
    void accept() throws Exception {
        when(gateway.post(anyString(), any(), anyString(), anyInt(), anyBoolean())).thenReturn(201);
    }

    @Test
    void subscribeValidationAndConfig() {
        String alice = registerAndGetBearer();
        assertThat(send("GET", alice, "/api/push/config", null)).bodyJson().extractingPath("$.publicKey")
                .isEqualTo(B64.encodeToString(uncompressed((ECPublicKey) SERVER.getPublic())));
        assertThat(mvc.get().uri("/api/push/config").exchange()).hasStatus(401);

        assertThat(send("PUT", alice, "/api/push/subscription", subscription("http://fcm.googleapis.com/x", false))).hasStatus(400);
        assertThat(send("PUT", alice, "/api/push/subscription", subscription("https://evil.example.com/x", false))).hasStatus(400);
        assertThat(send("PUT", alice, "/api/push/subscription", subscription("https://fcm.googleapis.com:8443/x", false))).hasStatus(400);
        assertThat(send("PUT", alice, "/api/push/subscription", """
                {"endpoint":"https://fcm.googleapis.com/fcm/send/abc","keys":{"p256dh":"AAAA","auth":"BBBB"}}""")).hasStatus(400);
        assertThat(send("PUT", alice, "/api/push/subscription", "{\"endpoint\":\"https://fcm.googleapis.com/x\"}")).hasStatus(400);
    }

    @Test
    void notificationAndChatArriveOnSubscribedDevices() throws Exception {
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String bobEndpoint = endpoint();
        String aliceEndpoint = endpoint();
        assertThat(send("PUT", bob, "/api/push/subscription", subscription(bobEndpoint, false)))
                .bodyJson().extractingPath("$.active").isEqualTo(true);
        assertThat(send("PUT", alice, "/api/push/subscription", subscription(aliceEndpoint, false))).hasStatusOk();
        verify(gateway, timeout(5000)).post(eq(aliceEndpoint), any(), anyString(), anyInt(), eq(false));
        verify(gateway, timeout(5000)).post(eq(bobEndpoint), any(), anyString(), anyInt(), eq(false));
        clearInvocations(gateway);

        send("POST", alice, "/api/friends/requests", "{\"userId\":\"%s\"}".formatted(userId(bob)));
        verify(gateway, timeout(5000)).post(eq(bobEndpoint), any(), startsWith("vapid t="), eq(86_400), eq(false));
        verify(gateway, never()).post(eq(aliceEndpoint), any(), anyString(), anyInt(), anyBoolean());

        String groupId = JsonPath.read(post(alice, "/api/groups", "{\"name\":\"Casa\"}"), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", bob, "/api/groups/join/" + token, null)).hasStatusOk();
        clearInvocations(gateway);
        post(alice, "/api/groups/" + groupId + "/chat/messages", "{\"body\":\"Oi, Bob\"}");
        verify(gateway, timeout(5000)).post(eq(bobEndpoint), any(), anyString(), anyInt(), eq(true));
        verify(gateway, after(500).never()).post(eq(aliceEndpoint), any(), anyString(), anyInt(), anyBoolean());
    }

    @Test
    void logoutStopsPushAndOnlyTheSameUserReactivatesAutomatically() throws Exception {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String carol = registerAndGetBearer();
        String device = endpoint();
        String bobId = userId(bob);
        String bobEmail = JsonPath.read(body(send("GET", bob, "/api/users/me", null)), "$.email");
        assertThat(send("PUT", bob, "/api/push/subscription", subscription(device, false))).hasStatusOk();
        verify(gateway, timeout(5000)).post(eq(device), any(), anyString(), anyInt(), anyBoolean());
        clearInvocations(gateway);

        assertThat(send("POST", bob, "/api/auth/logout", null)).hasStatus(204);
        send("POST", alice, "/api/friends/requests", "{\"userId\":\"%s\"}".formatted(bobId));
        verify(gateway, after(1000).never()).post(eq(device), any(), anyString(), anyInt(), anyBoolean());
        String bobAgain = login(bobEmail);

        // Outra pessoa no mesmo navegador: a sincronização automática não toma o aparelho.
        assertThat(send("PUT", carol, "/api/push/subscription", subscription(device, true)))
                .bodyJson().extractingPath("$.active").isEqualTo(false);
        assertThat(send("PUT", bobAgain, "/api/push/subscription", subscription(device, true)))
                .bodyJson().extractingPath("$.active").isEqualTo(true);
        assertThat(send("PUT", carol, "/api/push/subscription", subscription(endpoint(), true)))
                .bodyJson().extractingPath("$.active").isEqualTo(false);

        clearInvocations(gateway);
        String dave = registerAndGetBearer();
        send("POST", dave, "/api/friends/requests", "{\"userId\":\"%s\"}".formatted(bobId));
        verify(gateway, timeout(5000)).post(eq(device), any(), anyString(), anyInt(), anyBoolean());

        // Ativar de propósito passa o aparelho para quem ativou; desligar só vale para o dono.
        assertThat(send("PUT", carol, "/api/push/subscription", subscription(device, false))).hasStatusOk();
        assertThat(send("DELETE", bobAgain, "/api/push/subscription", "{\"endpoint\":\"%s\"}".formatted(device))).hasStatus(204);
        assertThat(send("PUT", carol, "/api/push/subscription", subscription(device, true)))
                .bodyJson().extractingPath("$.active").isEqualTo(true);
        assertThat(send("DELETE", carol, "/api/push/subscription", "{\"endpoint\":\"%s\"}".formatted(device))).hasStatus(204);
        assertThat(send("PUT", carol, "/api/push/subscription", subscription(device, true)))
                .bodyJson().extractingPath("$.active").isEqualTo(false);
    }

    @Test
    void goneSubscriptionIsRemoved() throws Exception {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String device = endpoint();
        when(gateway.post(eq(device), any(), anyString(), anyInt(), anyBoolean())).thenReturn(410);
        assertThat(send("PUT", bob, "/api/push/subscription", subscription(device, false))).hasStatusOk();

        send("POST", alice, "/api/friends/requests", "{\"userId\":\"%s\"}".formatted(userId(bob)));
        verify(gateway, timeout(5000)).post(eq(device), any(), anyString(), anyInt(), anyBoolean());
        Thread.sleep(300);
        assertThat(send("PUT", bob, "/api/push/subscription", subscription(device, true)))
                .bodyJson().extractingPath("$.active").isEqualTo(false);
    }

    private String login(String email) {
        return "Bearer " + JsonPath.read(body(mvc.post().uri("/api/auth/login")
                .header("X-Auth-Mode", "token").header(CSRF, "test")
                .contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"segredo123\"}".formatted(email))
                .exchange()), "$.token");
    }

    private static String endpoint() {
        return "https://fcm.googleapis.com/fcm/send/" + UUID.randomUUID();
    }

    private static String subscription(String endpoint, boolean auto) {
        String p256dh = B64.encodeToString(uncompressed((ECPublicKey) p256().getPublic()));
        return """
                {"endpoint":"%s","keys":{"p256dh":"%s","auth":"%s"},"auto":%s}"""
                .formatted(endpoint, p256dh, B64.encodeToString(new byte[16]), auto);
    }

    private static KeyPair p256() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] uncompressed(ECPublicKey key) {
        byte[] out = new byte[65];
        out[0] = 4;
        System.arraycopy(fixed(key.getW().getAffineX()), 0, out, 1, 32);
        System.arraycopy(fixed(key.getW().getAffineY()), 0, out, 33, 32);
        return out;
    }

    private static byte[] fixed(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, out, 32 - length, length);
        return out;
    }
}
