package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.planning.PriceReferenceService;
import com.lifepedia.api.planning.PriceReferenceService.Match;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlanningIntegrationTest extends ApiTestSupport {

    /** Sem rede nos testes: a busca de produto parecido é simulada. */
    @MockitoBean
    PriceReferenceService references;

    private final YearMonth now = YearMonth.now();

    @Test
    void simulationPriorityScenariosAndAcquire() {
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String outsider = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        send("POST", bob, "/api/groups/join/" + token, null);
        String base = "/api/groups/" + groupId + "/planning";

        // Base: 3000 − 1000 de fixos no grupo = 2000/mês; reserva de 1000.
        post(alice, "/api/finance/recurring", """
                {"description":"Salário","type":"INCOME","categoryId":"%s","amount":3000,"startMonth":"%s","groupIds":["%s"]}
                """.formatted(categoryId(alice, "INCOME", "Salário"), now, groupId));
        post(alice, "/api/finance/recurring", """
                {"description":"Aluguel","type":"EXPENSE","categoryId":"%s","amount":1000,"startMonth":"%s","groupIds":["%s"]}
                """.formatted(categoryId(alice, "EXPENSE", "Moradia"), now, groupId));
        assertThat(send("PUT", bob, base + "/settings", """
                {"reserve":1000,"averageMonths":5}""")).hasStatus(400);
        assertThat(send("PUT", bob, base + "/settings", """
                {"reserve":1000,"averageMonths":0}""")).hasStatusOk();

        String laptop = JsonPath.read(post(alice, base + "/items", item("Notebook", 5000, now.plusMonths(2), 1)), "$.id");
        String trip = JsonPath.read(post(bob, base + "/items", item("Viagem", 8000, now.plusMonths(3), 1)), "$.id");

        assertThat(send("GET", alice, base, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.base.monthly").asNumber().isEqualTo(2000.0);
            assertThat(json).extractingPath("$.items[0].item.name").isEqualTo("Notebook");
            assertThat(json).extractingPath("$.items[0].shortfall").asNumber().isEqualTo(0.0);
            assertThat(json).extractingPath("$.items[0].earliestMonth").isEqualTo(now.plusMonths(1).toString());
            assertThat(json).extractingPath("$.items[1].shortfall").asNumber().isEqualTo(4000.0);
            assertThat(json).extractingPath("$.items[1].item.canEdit").isEqualTo(true);
            assertThat(json).extractingPath("$.outcome.shortfall").asNumber().isEqualTo(4000.0);
            assertThat(json).extractingPath("$.outcome.lowestMonth").isEqualTo(now.plusMonths(3).toString());
        });
        // Bob não edita item da Alice; quem não é do grupo não vê nada.
        assertThat(send("PUT", bob, base + "/items/" + laptop, item("X", 1, now, 1))).hasStatus(403);
        assertThat(send("GET", outsider, base, null)).hasStatus(404);
        assertThat(send("POST", outsider, base + "/items", item("X", 1, now, 1))).hasStatus(404);

        // Viagem primeiro: agora falta para o notebook.
        assertThat(send("PUT", bob, base + "/items/order", """
                {"itemIds":["%s","%s"]}""".formatted(trip, laptop))).hasStatus(204);
        assertThat(send("GET", alice, base, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[0].item.name").isEqualTo("Viagem");
            assertThat(json).extractingPath("$.items[0].shortfall").asNumber().isEqualTo(0.0);
            assertThat(json).extractingPath("$.items[1].shortfall").asNumber().isEqualTo(4000.0);
        });

        assertThat(send("PUT", bob, base + "/items/" + laptop + "/active", """
                {"active":false}""")).hasStatus(204);
        assertThat(send("GET", alice, base, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.outcome.count").isEqualTo(1);
            assertThat(json).extractingPath("$.outcome.shortfall").asNumber().isEqualTo(0.0);
            assertThat(json).extractingPath("$.items[1].considered").isEqualTo(false);
            assertThat(json).extractingPath("$.items[1].shortfall").asNumber().isEqualTo(4000.0);
        });

        // Cenário só com o notebook: compara, simula com "e se" e aplica.
        String scenario = JsonPath.read(post(bob, base + "/scenarios", """
                {"name":"Só notebook","itemIds":["%s"]}""".formatted(laptop)), "$.id");
        assertThat(send("GET", alice, base + "/scenarios/compare", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$[0].name").isEqualTo("Itens ativos");
            assertThat(json).extractingPath("$[1].name").isEqualTo("Só notebook");
            assertThat(json).extractingPath("$[1].outcome.total").asNumber().isEqualTo(5000.0);
        });
        assertThat(send("GET", alice, base + "?scenario=" + scenario + "&extraExpense=2000", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.base.monthly").asNumber().isEqualTo(0.0);
            assertThat(json).extractingPath("$.outcome.shortfall").asNumber().isEqualTo(4000.0);
        });
        assertThat(send("GET", alice, base + "?extraIncome=-1", null)).hasStatus(400);
        assertThat(send("DELETE", alice, base + "/scenarios/" + scenario, null)).hasStatus(204); // admin
        post(alice, base + "/scenarios", """
                {"name":"Atual"}""");
        assertThat(send("POST", bob, base + "/scenarios/" + scenario + "/apply", null)).hasStatus(404);

        // Comprado: sai da simulação e vira despesa (Alice lança com a categoria dela).
        String purchases = categoryId(alice, "EXPENSE", "Compras");
        assertThat(send("POST", alice, base + "/items/" + laptop + "/acquire", """
                {"date":"%s","amount":4800,"launch":true,"categoryId":"%s"}""".formatted(LocalDate.now(), purchases)))
                .hasStatus(204);
        assertThat(send("GET", alice, base, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items.length()").isEqualTo(1);
            assertThat(json).extractingPath("$.acquired[0].name").isEqualTo("Notebook");
            assertThat(json).extractingPath("$.acquired[0].acquiredAmount").asNumber().isEqualTo(4800.0);
        });
        assertThat(send("GET", alice, "/api/groups/" + groupId + "/finance/transactions?month=" + now, null))
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.items[0].description").isEqualTo("Notebook");
                    assertThat(json).extractingPath("$.items[0].synced").isEqualTo(false);
                });

        // Quem sai leva os itens que cadastrou.
        assertThat(send("POST", bob, "/api/groups/" + groupId + "/leave", null)).hasStatus(204);
        assertThat(send("GET", alice, base, null)).bodyJson().extractingPath("$.items.length()").isEqualTo(0);
    }

    @Test
    void imageLinkAndValidation() throws IOException {
        String alice = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String base = "/api/groups/" + groupId + "/planning";

        String png = "data:image/png;base64," + Base64.getEncoder().encodeToString(png(200, 120));
        String created = post(alice, base + "/items", """
                {"name":"Sofá","kind":"PURCHASE","amount":2500,"targetMonth":"%s","url":"loja.com.br/sofa?cor=azul",
                 "linkTitle":"Sofá 3 lugares","linkSite":"Loja","linkPrice":2499.9,"imageData":"%s"}
                """.formatted(now.plusMonths(4), png));
        String id = JsonPath.read(created, "$.id");
        String imageUrl = JsonPath.read(created, "$.imageUrl");
        assertThat((String) JsonPath.read(created, "$.url")).isEqualTo("https://loja.com.br/sofa?cor=azul");
        assertThat(send("GET", alice, imageUrl, null)).hasStatusOk().hasContentType("image/jpeg");
        assertThat(send("GET", registerAndGetBearer(), imageUrl, null)).hasStatus(404);

        assertThat(send("PUT", alice, base + "/items/" + id, """
                {"name":"Sofá","kind":"PURCHASE","amount":2500,"targetMonth":"%s","removeImage":true}
                """.formatted(now.plusMonths(4)))).bodyJson().extractingPath("$.imageUrl").isNull();

        assertThat(send("POST", alice, base + "/items", """
                {"name":"X","kind":"PURCHASE","amount":1,"targetMonth":"%s","url":"javascript:alert(1)"}
                """.formatted(now))).hasStatus(400);
        assertThat(send("POST", alice, base + "/items", """
                {"name":"X","kind":"PURCHASE","amount":1,"targetMonth":"%s","imageData":"data:image/png;base64,AAAA"}
                """.formatted(now))).hasStatus(400);
        assertThat(send("POST", alice, base + "/items", """
                {"name":"X","kind":"PURCHASE","amount":1,"targetMonth":"%s"}""".formatted(now.plusYears(11)))).hasStatus(400);
        assertThat(send("POST", alice, base + "/items", """
                {"name":"","kind":"PURCHASE","amount":0,"targetMonth":"%s","installments":200}""".formatted(now)))
                .hasStatus(400);

        // Endereço interno: a API não busca (vem como bloqueado, só com o nome do link).
        assertThat(send("POST", alice, base + "/link-preview", """
                {"url":"http://127.0.0.1/produto-legal-azul"}""")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.blocked").isEqualTo(true);
            assertThat(json).extractingPath("$.title").isEqualTo("Produto legal azul");
            assertThat(json).extractingPath("$.imageData").isNull();
            assertThat(json).extractingPath("$.reference").isNull();
        });

        // Loja bloqueou: preço de produto parecido vem como referência.
        when(references.find("Produto bom")).thenReturn(Optional.of(new Match("Produto Bom 2", new BigDecimal("99.90"),
                "https://www.buscape.com.br/p/produto-bom", List.of(), 4, "Loja X")));
        assertThat(send("POST", alice, base + "/link-preview", """
                {"url":"http://127.0.0.1/produto-bom"}""")).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.blocked").isEqualTo(true);
            assertThat(json).extractingPath("$.price").isNull();
            assertThat(json).extractingPath("$.reference.site").isEqualTo("Buscapé");
            assertThat(json).extractingPath("$.reference.price").isEqualTo(99.9);
            assertThat(json).extractingPath("$.reference.stores").isEqualTo(4);
        });
        assertThat(send("POST", alice, base + "/price-reference", """
                {"name":"Produto bom"}""")).bodyJson().extractingPath("$.reference.title").isEqualTo("Produto Bom 2");
        assertThat(send("POST", alice, base + "/price-reference", """
                {"name":"Outra coisa"}""")).bodyJson().extractingPath("$.reference").isNull();
        assertThat(send("POST", registerAndGetBearer(), base + "/price-reference", """
                {"name":"Produto bom"}""")).hasStatus(404);
        String withReference = post(alice, base + "/items", """
                {"name":"Produto bom","kind":"PURCHASE","amount":99.9,"targetMonth":"%s","url":"loja.com.br/produto-bom",
                 "referenceSite":"Buscapé","referenceTitle":"Produto Bom 2","referenceUrl":"https://www.buscape.com.br/p/produto-bom",
                 "referencePrice":99.9}""".formatted(now.plusMonths(2)));
        assertThat((String) JsonPath.read(withReference, "$.referenceSite")).isEqualTo("Buscapé");
        assertThat((String) JsonPath.read(withReference, "$.referenceUrl")).isEqualTo("https://www.buscape.com.br/p/produto-bom");
        assertThat(send("POST", alice, base + "/items", """
                {"name":"X","kind":"PURCHASE","amount":1,"targetMonth":"%s","referenceSite":"B","referenceUrl":"javascript:x"}
                """.formatted(now))).hasStatus(400);
        assertThat(send("POST", alice, base + "/link-preview", """
                {"url":"file:///etc/passwd"}""")).hasStatus(400);
    }

    @Test
    void groupOnlyEntriesAndOccasionalAverage() {
        String alice = registerAndGetBearer("Alice", newUsername());
        String outsider = registerAndGetBearer();
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String finance = "/api/groups/" + groupId + "/finance";
        String planning = "/api/groups/" + groupId + "/planning";
        String food = categoryId(alice, "EXPENSE", "Alimentação");
        send("PUT", alice, planning + "/settings", """
                {"reserve":0,"averageMonths":3}""");

        // Só o mês atual lançado: a média usa ele (antes dava 0 por não haver mês completo).
        assertThat(send("POST", alice, finance + "/transactions", """
                {"description":"Mercado","type":"EXPENSE","categoryId":"%s","amount":600,"date":"%s-10"}
                """.formatted(food, now))).hasStatus(201).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.category.name").isEqualTo("Alimentação");
            assertThat(json).extractingPath("$.mine").isEqualTo(true);
            assertThat(json).extractingPath("$.synced").isEqualTo(false);
        });
        assertThat(send("GET", alice, planning, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.base.occasionalAverage").asNumber().isEqualTo(-600.0);
            assertThat(json).extractingPath("$.base.averageBasis[0]").isEqualTo(now.toString());
        });

        // Com mês completo lançado, só ele entra (mês sem nada não puxa para baixo; o atual fica de fora).
        post(alice, finance + "/transactions", """
                {"description":"Farmácia","type":"EXPENSE","categoryId":"%s","amount":300,"date":"%s-05"}
                """.formatted(food, now.minusMonths(2)));
        assertThat(send("GET", alice, planning, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.base.occasionalAverage").asNumber().isEqualTo(-300.0);
            assertThat(json).extractingPath("$.base.averageBasis.length()").asNumber().isEqualTo(1);
        });

        // Fixo só do grupo entra na base.
        assertThat(send("POST", alice, finance + "/recurring", """
                {"description":"Salário","type":"INCOME","categoryId":"%s","amount":2000}
                """.formatted(categoryId(alice, "INCOME", "Salário")))).hasStatus(201);
        assertThat(send("GET", alice, planning, null)).bodyJson()
                .extractingPath("$.base.fixedIncome").asNumber().isEqualTo(2000.0);
        assertThat(send("GET", alice, finance + "/transactions?month=" + now, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.items[0].description").isEqualTo("Mercado");
            assertThat(json).extractingPath("$.items[0].synced").isEqualTo(false);
            assertThat(json).extractingPath("$.items[0].mine").isEqualTo(true);
        });

        // Validação, categoria de outro usuário e quem não é do grupo.
        assertThat(send("POST", alice, finance + "/transactions", """
                {"description":"","type":"EXPENSE","categoryId":"%s","amount":0,"date":"%s-10"}
                """.formatted(food, now))).hasStatus(400);
        assertThat(send("POST", alice, finance + "/transactions", """
                {"description":"X","type":"EXPENSE","categoryId":"%s","amount":1,"date":"%s-10"}
                """.formatted(categoryId(outsider, "EXPENSE", "Alimentação"), now))).hasStatus(400);
        assertThat(send("POST", outsider, finance + "/recurring", """
                {"description":"X","type":"EXPENSE","categoryId":"%s","amount":1}
                """.formatted(categoryId(outsider, "EXPENSE", "Alimentação")))).hasStatus(404);
    }

    private static String item(String name, int amount, YearMonth target, int installments) {
        return """
                {"name":"%s","kind":"PURCHASE","amount":%d,"targetMonth":"%s","installments":%d}
                """.formatted(name, amount, target, installments);
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
}
