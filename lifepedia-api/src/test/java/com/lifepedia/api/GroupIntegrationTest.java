package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.config.StorageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class GroupIntegrationTest extends ApiTestSupport {

    @Autowired
    StorageProperties storage;

    private final String month = YearMonth.now().toString();
    private final String day = YearMonth.now().atDay(1).toString();

    @Test
    void invitesNotificationsLinkAndMembership() {
        String aliceName = newUsername();
        String bobName = newUsername();
        String alice = registerAndGetBearer("Alice Souza", aliceName);
        String bob = registerAndGetBearer("Bob Lima", bobName);
        String carol = registerAndGetBearer("Carol", newUsername());
        String bobId = userId(bob);

        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");

        String candidates = "/api/groups/" + groupId + "/invite-candidates?q=@" + bobName.substring(0, 8);
        assertThat(send("GET", alice, candidates, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$[0].id").isEqualTo(bobId);
            assertThat(json).extractingPath("$[0].status").isNull();
        });
        assertThat(send("GET", bob, candidates, null)).hasStatus(404);

        assertThat(send("POST", alice, "/api/groups/" + groupId + "/invites", """
                {"userIds":["%s"]}""".formatted(bobId))).bodyJson().extractingPath("$.invited").isEqualTo(1);
        assertThat(send("POST", alice, "/api/groups/" + groupId + "/invites", """
                {"userIds":["%s"]}""".formatted(bobId))).bodyJson().extractingPath("$.skipped").isEqualTo(1);
        assertThat(send("GET", alice, candidates, null)).bodyJson().extractingPath("$[0].status").isEqualTo("INVITED");

        assertThat(send("GET", bob, "/api/notifications/unread-count", null)).bodyJson().extractingPath("$.count").isEqualTo(1);
        assertThat(send("GET", bob, "/api/notifications", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[0].type").isEqualTo("GROUP_INVITE");
            assertThat(json).extractingPath("$.items[0].data.actorName").isEqualTo("Alice Souza");
            assertThat(json).extractingPath("$.items[0].data.groupName").isEqualTo("Casa");
        });
        assertThat(send("GET", carol, "/api/notifications/unread-count", null)).bodyJson().extractingPath("$.count").isEqualTo(0);

        assertThat(send("GET", bob, "/api/groups/" + groupId, null)).hasStatus(404);
        String inviteId = JsonPath.read(body(send("GET", bob, "/api/groups/invites", null)), "$[0].id");
        assertThat(send("POST", carol, "/api/groups/invites/" + inviteId + "/accept", null)).hasStatus(404);
        assertThat(send("POST", bob, "/api/groups/invites/" + inviteId + "/accept", null))
                .bodyJson().extractingPath("$.memberCount").isEqualTo(2);
        assertThat(send("GET", bob, "/api/groups/invites/count", null)).bodyJson().extractingPath("$.count").isEqualTo(0);

        // Membro comum não convida nem gera link.
        assertThat(send("POST", bob, "/api/groups/" + groupId + "/invite-link", null)).hasStatus(403);

        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("GET", carol, "/api/groups/join/" + token, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.name").isEqualTo("Casa");
            assertThat(json).extractingPath("$.member").isEqualTo(false);
        });
        assertThat(send("POST", carol, "/api/groups/join/" + token, null)).bodyJson().extractingPath("$.memberCount").isEqualTo(3);
        assertThat(send("GET", carol, "/api/groups/join/bad-token", null)).hasStatus(404);

        String carolId = userId(carol);
        assertThat(send("DELETE", bob, "/api/groups/" + groupId + "/members/" + carolId, null)).hasStatus(403);
        assertThat(send("DELETE", alice, "/api/groups/" + groupId + "/members/" + carolId, null)).hasStatus(204);
        assertThat(send("GET", carol, "/api/groups/" + groupId, null)).hasStatus(404);

        // Último admin sai: o membro restante vira admin; último membro sai: o grupo some.
        assertThat(send("POST", alice, "/api/groups/" + groupId + "/leave", null)).hasStatus(204);
        assertThat(send("GET", bob, "/api/groups/" + groupId, null)).bodyJson().extractingPath("$.role").isEqualTo("ADMIN");
        assertThat(send("POST", bob, "/api/groups/" + groupId + "/leave", null)).hasStatus(204);
        assertThat(send("GET", bob, "/api/groups", null)).bodyJson().extractingPath("$.length()").isEqualTo(0);
    }

    @Test
    void sharedItemsSyncCopyAndMetrics() {
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String outsider = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casal"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        send("POST", bob, "/api/groups/join/" + token, null);
        String base = "/api/groups/" + groupId + "/finance";

        String aliceFood = categoryId(alice, "EXPENSE", "Alimentação");
        String aliceHouse = categoryId(alice, "EXPENSE", "Moradia");
        String aliceTx = JsonPath.read(post(alice, "/api/finance/transactions", """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":100,"date":"%s","groupIds":["%s"]}
                """.formatted(aliceFood, day, groupId)), "$.id");
        String aliceRent = JsonPath.read(post(alice, "/api/finance/recurring", """
                {"description":"Aluguel","type":"EXPENSE","categoryId":"%s","amount":1000,"startMonth":"%s",
                 "groupIds":["%s"],"shareMode":"COPY"}
                """.formatted(aliceHouse, month, groupId)), "$.id");
        assertThat(send("POST", outsider, "/api/finance/transactions", """
                {"description":"X","type":"EXPENSE","categoryId":"%s","amount":1,"date":"%s","groupIds":["%s"]}
                """.formatted(categoryId(outsider, "EXPENSE", "Alimentação"), day, groupId))).hasStatus(404);

        String bobTx = JsonPath.read(post(bob, "/api/finance/transactions", """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":50,"date":"%s"}
                """.formatted(categoryId(bob, "EXPENSE", "Alimentação"), day)), "$.id");
        String share = """
                {"transactionIds":["%s"],"mode":"SYNC"}""".formatted(bobTx);
        assertThat(send("POST", bob, base + "/items", share)).bodyJson().extractingPath("$.added").isEqualTo(1);
        assertThat(send("POST", bob, base + "/items", share)).bodyJson().extractingPath("$.skipped").isEqualTo(1);
        assertThat(send("POST", bob, base + "/items", """
                {"transactionIds":["%s"],"mode":"SYNC"}""".formatted(aliceTx))).hasStatus(404);

        assertThat(send("GET", alice, base + "/summary?month=" + month, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.total.expense").asNumber().isEqualTo(1150.0);
            assertThat(json).extractingPath("$.expensesByCategory[0].name").isEqualTo("Moradia");
            assertThat(json).extractingPath("$.expensesByCategory[1].occasional").asNumber().isEqualTo(150.0);
            assertThat(json).extractingPath("$.members[0].expense").asNumber().isEqualTo(1100.0);
        });
        assertThat(send("GET", alice, base + "/summary/category-items?category=EXPENSE:alimentação&month=" + month, null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$[0].count").isEqualTo(2);
                    assertThat(json).extractingPath("$[0].total").asNumber().isEqualTo(150.0);
                });

        // Sincronizado acompanha o pessoal.
        send("PUT", alice, "/api/finance/transactions/" + aliceTx, """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":120,"date":"%s"}
                """.formatted(aliceFood, day));
        assertThat(send("GET", bob, base + "/summary?month=" + month, null))
                .bodyJson().extractingPath("$.occasional.expense").asNumber().isEqualTo(170.0);
        send("DELETE", alice, "/api/finance/transactions/" + aliceTx, null);
        assertThat(send("GET", bob, base + "/summary?month=" + month, null))
                .bodyJson().extractingPath("$.occasional.expense").asNumber().isEqualTo(50.0);

        // Cópia: só quem levou edita; sincronizado não se edita no grupo.
        String copyId = JsonPath.read(body(send("GET", bob, base + "/recurring", null)), "$.items[0].id");
        String rent = """
                {"description":"Aluguel","type":"EXPENSE","categoryId":"%s","amount":900,"startMonth":"%s"}
                """.formatted(aliceHouse, month);
        assertThat(send("PUT", bob, base + "/recurring/" + copyId, rent)).hasStatus(403);
        assertThat(send("PUT", alice, base + "/recurring/" + copyId, rent)).hasStatus(204);
        assertThat(send("GET", bob, base + "/summary?month=" + month, null))
                .bodyJson().extractingPath("$.fixed.expense").asNumber().isEqualTo(900.0);

        MvcTestResult page = send("GET", alice, base + "/transactions?month=" + month + "&sort=member,asc", null);
        assertThat(page).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.totalItems").isEqualTo(1);
            assertThat(json).extractingPath("$.items[0].member.name").isEqualTo("Bob");
            assertThat(json).extractingPath("$.items[0].synced").isEqualTo(true);
            assertThat(json).extractingPath("$.items[0].mine").isEqualTo(false);
        });
        String bobGroupTx = JsonPath.read(body(page), "$.items[0].id");
        assertThat(send("PUT", bob, base + "/transactions/" + bobGroupTx, """
                {"description":"X","type":"EXPENSE","categoryId":"%s","amount":1,"date":"%s"}
                """.formatted(categoryId(bob, "EXPENSE", "Alimentação"), day))).hasStatus(400);
        List<String> origins = JsonPath.read(body(send("GET", bob, base + "/origins", null)), "$.transactions[*].originId");
        assertThat(origins).containsExactly(bobTx);

        // Categoria com cópia em grupo conta como em uso (a cópia fica mesmo sem o fixo pessoal).
        assertThat(send("DELETE", alice, "/api/finance/recurring/" + aliceRent, null)).hasStatus(204);
        assertThat(send("DELETE", alice, "/api/finance/categories/" + aliceHouse, null)).hasStatus(409);

        assertThat(send("GET", outsider, base + "/summary", null)).hasStatus(404);
        assertThat(send("GET", outsider, base + "/transactions", null)).hasStatus(404);

        // Quem sai leva seus itens.
        send("POST", bob, "/api/groups/" + groupId + "/leave", null);
        assertThat(send("GET", alice, base + "/summary?month=" + month, null))
                .bodyJson().extractingPath("$.occasional.expense").asNumber().isEqualTo(0);
        assertThat(send("GET", bob, "/api/finance/transactions/all?month=" + month, null))
                .bodyJson().extractingPath("$.length()").isEqualTo(1);
    }

    @Test
    void groupImageIsValidatedAndPrivate() throws Exception {
        String alice = registerAndGetBearer();
        String outsider = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Fotos"}"""), "$.id");

        assertThat(upload(alice, groupId, "não é imagem".getBytes())).hasStatus(400);
        assertThat(upload(alice, groupId, png(32))).hasStatus(400);
        MvcTestResult ok = upload(alice, groupId, png(800));
        assertThat(ok).hasStatusOk();
        String imageUrl = JsonPath.read(body(ok), "$.imageUrl");
        assertThat(imageUrl).startsWith("/api/groups/" + groupId + "/image?v=");

        MvcTestResult image = send("GET", alice, imageUrl, null);
        assertThat(image).hasStatusOk().hasContentType("image/jpeg");
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(image.getResponse().getContentAsByteArray()));
        assertThat(stored.getWidth()).isEqualTo(512);
        assertThat(send("GET", outsider, imageUrl, null)).hasStatus(404);

        Path first = groupsDir().resolve(imageKey(imageUrl) + ".jpg");
        assertThat(first).exists();
        String replacedUrl = JsonPath.read(body(upload(alice, groupId, png(600))), "$.imageUrl");
        Path second = groupsDir().resolve(imageKey(replacedUrl) + ".jpg");
        assertThat(first).doesNotExist();
        assertThat(second).exists();

        assertThat(send("DELETE", alice, "/api/groups/" + groupId + "/image", null))
                .bodyJson().extractingPath("$.imageUrl").isNull();
        assertThat(second).doesNotExist();
    }

    private MvcTestResult upload(String bearer, String groupId, byte[] bytes) {
        return uploadImage(bearer, "/api/groups/" + groupId + "/image", bytes);
    }

    private Path groupsDir() {
        return storage.dir().resolve("groups").toAbsolutePath().normalize();
    }
}
