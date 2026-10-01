package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TaskIntegrationTest extends ApiTestSupport {

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    private String agenda(String bearer, LocalDate from, LocalDate to, boolean overdue) {
        MvcTestResult result = send("GET", bearer, "/api/tasks/agenda?from=%s&to=%s&overdue=%s".formatted(from, to, overdue), null);
        assertThat(result).hasStatusOk();
        return body(result);
    }

    private String todayView(String bearer) {
        return agenda(bearer, today, today, true);
    }

    private static List<String> titles(String json, String path) {
        return JsonPath.read(json, path + "[*].title");
    }

    private static List<String> allTitles(String agenda) {
        return JsonPath.read(agenda, "$.days[*].items[*].title");
    }

    private String create(String bearer, String json) {
        return JsonPath.read(post(bearer, "/api/tasks", json), "$.id");
    }

    @Test
    void oneOffTasksOverdueCompleteUndoAndMove() {
        String me = registerAndGetBearer();
        String today1 = create(me, """
                {"title":"Ligar dentista","dueDate":"%s","dueTime":"09:30","durationMinutes":30,"priority":"HIGH"}
                """.formatted(today));
        create(me, """
                {"title":"Pagar boleto","dueDate":"%s"}""".formatted(today.minusDays(3)));
        String undated = create(me, """
                {"title":"Ler livro","subtasks":[{"title":"Cap. 1"},{"title":"Cap. 2"}]}""");
        create(me, """
                {"title":"Revisão do carro","dueDate":"%s"}""".formatted(today.plusDays(2)));

        String view = todayView(me);
        assertThat(JsonPath.<String>read(view, "$.today")).isEqualTo(today.toString());
        assertThat(titles(view, "$.overdue")).containsExactly("Pagar boleto");
        assertThat(JsonPath.<Boolean>read(view, "$.overdue[0].overdue")).isTrue();
        assertThat(titles(view, "$.days[0].items")).containsExactly("Ligar dentista");
        assertThat(JsonPath.<String>read(view, "$.days[0].items[0].dueTime")).isEqualTo("09:30");
        assertThat(titles(body(send("GET", me, "/api/tasks/undated", null)), "$")).containsExactly("Ler livro");
        assertThat(titles(agenda(me, today.plusDays(1), today.plusDays(7), false), "$.days[1].items"))
                .containsExactly("Revisão do carro");

        // Concluir, desfazer, concluir de novo; aparece no histórico.
        assertThat(send("PUT", me, "/api/tasks/" + today1 + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(today))).hasStatus(204);
        view = todayView(me);
        assertThat(JsonPath.<String>read(view, "$.days[0].items[0].status")).isEqualTo("DONE");
        assertThat(send("PUT", me, "/api/tasks/" + today1 + "/status", """
                {"date":"%s","status":"PENDING"}""".formatted(today))).hasStatus(204);
        assertThat(JsonPath.<String>read(todayView(me), "$.days[0].items[0].status")).isEqualTo("PENDING");
        assertThat(send("PUT", me, "/api/tasks/" + today1 + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(today))).hasStatus(204);
        String history = body(send("GET", me, "/api/tasks/history", null));
        assertThat(titles(history, "$.items")).containsExactly("Ligar dentista");
        assertThat(send("PUT", me, "/api/tasks/" + today1 + "/status", """
                {"date":"%s","status":"SKIPPED"}""".formatted(today))).hasStatus(400);

        // Subtarefa marcada em tarefa sem data; adiar a sem data para amanhã leva a marcação junto.
        String sub = JsonPath.read(body(send("GET", me, "/api/tasks/" + undated, null)), "$.subtasks[0].id");
        assertThat(send("PUT", me, "/api/tasks/" + undated + "/subtasks/" + sub + "/check", """
                {"date":null,"done":true}""")).hasStatus(204);
        assertThat(JsonPath.<Boolean>read(body(send("GET", me, "/api/tasks/undated", null)), "$[0].subtasks[0].done")).isTrue();
        assertThat(send("PUT", me, "/api/tasks/" + undated + "/move", """
                {"date":null,"to":"%s"}""".formatted(today.plusDays(1)))).hasStatus(204);
        view = agenda(me, today.plusDays(1), today.plusDays(1), false);
        assertThat(titles(view, "$.days[0].items")).containsExactly("Ler livro");
        assertThat(JsonPath.<Boolean>read(view, "$.days[0].items[0].subtasks[0].done")).isTrue();

        // Ordem por prioridade/horário vem da API.
        create(me, """
                {"title":"Beber água","dueDate":"%s","priority":"LOW"}""".formatted(today));
        assertThat(titles(body(send("GET", me, "/api/tasks/agenda?from=%s&to=%s&sort=PRIORITY".formatted(today, today), null)),
                "$.days[0].items")).containsExactly("Beber água", "Ligar dentista");
    }

    @Test
    void recurringSkipMoveEditThisAndFollowing() {
        String me = registerAndGetBearer();
        LocalDate start = today.minusDays(2);
        String id = create(me, """
                {"title":"Caminhar","dueDate":"%s","repeat":{"unit":"DAY","interval":1},"habit":true,
                 "subtasks":[{"title":"Alongar"}]}""".formatted(start));

        // Duas atrasadas + hoje.
        String view = todayView(me);
        assertThat(titles(view, "$.overdue")).containsExactly("Caminhar", "Caminhar");
        assertThat(titles(view, "$.days[0].items")).containsExactly("Caminhar");

        // Pular todas as atrasadas.
        assertThat(JsonPath.<Integer>read(body(send("PUT", me, "/api/tasks/" + id + "/overdue", """
                {"status":"SKIPPED"}""")), "$.updated")).isEqualTo(2);
        assertThat(titles(todayView(me), "$.overdue")).isEmpty();

        // Adiar só a de amanhã para depois de amanhã: lá ficam duas.
        assertThat(send("PUT", me, "/api/tasks/" + id + "/move", """
                {"date":"%s","to":"%s"}""".formatted(today.plusDays(1), today.plusDays(2)))).hasStatus(204);
        view = agenda(me, today.plusDays(1), today.plusDays(2), false);
        assertThat(titles(view, "$.days[0].items")).isEmpty();
        assertThat(JsonPath.<List<String>>read(view, "$.days[1].items[*].date"))
                .containsExactlyInAnyOrder(today.plusDays(1).toString(), today.plusDays(2).toString());

        // Editar só a de hoje.
        assertThat(send("PUT", me, "/api/tasks/" + id + "/occurrence", """
                {"date":"%s","moveTo":"%s","title":"Caminhar 5 km","dueTime":"07:00","priority":"HIGH"}
                """.formatted(today, today))).hasStatus(204);
        view = todayView(me);
        assertThat(JsonPath.<String>read(view, "$.days[0].items[0].title")).isEqualTo("Caminhar 5 km");
        assertThat(JsonPath.<Boolean>read(view, "$.days[0].items[0].edited")).isTrue();
        assertThat(titles(agenda(me, today.plusDays(3), today.plusDays(3), false), "$.days[0].items"))
                .containsExactly("Caminhar");

        // Ocorrência fora da regra → 400.
        assertThat(send("PUT", me, "/api/tasks/" + id + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(start.minusDays(1)))).hasStatus(400);

        // Esta e as próximas a partir de daqui a 5 dias: vira "a cada 2 dias".
        LocalDate split = today.plusDays(5);
        String next = JsonPath.read(body(send("PUT", me, "/api/tasks/" + id + "?from=" + split, """
                {"title":"Correr","dueDate":"%s","repeat":{"unit":"DAY","interval":2},"habit":true}
                """.formatted(split))), "$.id");
        assertThat(next).isNotEqualTo(id);
        view = agenda(me, today.plusDays(4), today.plusDays(8), false);
        assertThat(titles(view, "$.days[0].items")).containsExactly("Caminhar");
        assertThat(titles(view, "$.days[1].items")).containsExactly("Correr");
        assertThat(titles(view, "$.days[2].items")).isEmpty();
        assertThat(titles(view, "$.days[3].items")).containsExactly("Correr");
        assertThat(JsonPath.<String>read(body(send("GET", me, "/api/tasks/" + id, null)), "$.repeat.until"))
                .isEqualTo(split.minusDays(1).toString());

        // Excluir esta e as próximas da série nova a partir do 2º dia dela.
        assertThat(send("DELETE", me, "/api/tasks/" + next + "?scope=FOLLOWING&date=" + split.plusDays(2), null)).hasStatus(204);
        assertThat(allTitles(agenda(me, split, split.plusDays(10), false))).containsExactly("Correr");
    }

    @Test
    void repeatFromCompletionAdvancesAndUndoes() {
        String me = registerAndGetBearer();
        String id = create(me, """
                {"title":"Regar plantas","dueDate":"%s","repeat":{"unit":"DAY","interval":3,"fromCompletion":true,"count":2}}
                """.formatted(today.minusDays(1)));
        assertThat(titles(todayView(me), "$.overdue")).containsExactly("Regar plantas");

        // Concluir a atrasada: a próxima conta de hoje; a concluída continua no dia dela.
        assertThat(send("PUT", me, "/api/tasks/" + id + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(today.minusDays(1)))).hasStatus(204);
        assertThat(JsonPath.<String>read(body(send("GET", me, "/api/tasks/" + id, null)), "$.dueDate"))
                .isEqualTo(today.plusDays(3).toString());
        String view = agenda(me, today.minusDays(1), today.plusDays(3), false);
        assertThat(JsonPath.<String>read(view, "$.days[0].items[0].status")).isEqualTo("DONE");
        assertThat(titles(view, "$.days[4].items")).containsExactly("Regar plantas");

        // Desfazer volta a série; concluir de novo e a 2ª (última) encerra.
        assertThat(send("PUT", me, "/api/tasks/" + id + "/status", """
                {"date":"%s","status":"PENDING"}""".formatted(today.minusDays(1)))).hasStatus(204);
        assertThat(JsonPath.<String>read(body(send("GET", me, "/api/tasks/" + id, null)), "$.dueDate"))
                .isEqualTo(today.minusDays(1).toString());
        send("PUT", me, "/api/tasks/" + id + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(today.minusDays(1)));
        send("PUT", me, "/api/tasks/" + id + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(today.plusDays(3)));
        assertThat(JsonPath.<String>read(body(send("GET", me, "/api/tasks/" + id, null)), "$.completedAt")).isNotNull();
        assertThat(allTitles(agenda(me, today.plusDays(4), today.plusDays(20), true))).isEmpty();
    }

    @Test
    void labelsValidationAndIsolation() {
        String me = registerAndGetBearer();
        String other = registerAndGetBearer();
        String list = JsonPath.read(post(me, "/api/tasks/lists", """
                {"name":"Casa","color":"#2a78d6"}"""), "$.id");
        String tag = JsonPath.read(post(me, "/api/tasks/tags", """
                {"name":"mercado","color":"#e0a030"}"""), "$.id");
        assertThat(send("POST", me, "/api/tasks/lists", """
                {"name":"casa","color":"#2a78d6"}""")).hasStatus(409);
        String id = create(me, """
                {"title":"Comprar pão","dueDate":"%s","listId":"%s","tagIds":["%s"]}""".formatted(today, list, tag));
        String labels = body(send("GET", me, "/api/tasks/labels", null));
        assertThat(JsonPath.<Integer>read(labels, "$.lists[0].openCount")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(labels, "$.tags[0].openCount")).isEqualTo(1);
        assertThat(titles(body(send("GET", me, "/api/tasks/agenda?from=%s&to=%s&tag=%s".formatted(today, today, tag), null)),
                "$.days[0].items")).containsExactly("Comprar pão");

        // Validação.
        for (String invalid : List.of(
                "{\"title\":\" \"}",
                "{\"title\":\"x\",\"dueTime\":\"10:00\"}",
                "{\"title\":\"x\",\"habit\":true,\"dueDate\":\"%s\"}".formatted(today),
                "{\"title\":\"x\",\"repeat\":{\"unit\":\"DAY\",\"interval\":1}}",
                "{\"title\":\"x\",\"dueDate\":\"%s\",\"repeat\":{\"unit\":\"DAY\",\"interval\":0}}".formatted(today),
                "{\"title\":\"x\",\"dueDate\":\"%s\",\"repeat\":{\"unit\":\"DAY\",\"interval\":1,\"count\":2,\"until\":\"%s\"}}"
                        .formatted(today, today.plusDays(9)))) {
            assertThat(send("POST", me, "/api/tasks", invalid)).as(invalid).hasStatus(400);
        }
        assertThat(send("GET", me, "/api/tasks/agenda?from=%s&to=%s".formatted(today, today.plusDays(90)), null)).hasStatus(400);

        // Outro usuário: não vê nem usa.
        assertThat(send("GET", other, "/api/tasks/" + id, null)).hasStatus(404);
        assertThat(send("PUT", other, "/api/tasks/" + id + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(today))).hasStatus(404);
        assertThat(send("DELETE", other, "/api/tasks/lists/" + list, null)).hasStatus(404);
        assertThat(send("PUT", other, "/api/tasks/order", """
                {"taskIds":["%s"]}""".formatted(id))).hasStatus(404);
        assertThat(send("POST", other, "/api/tasks", """
                {"title":"x","listId":"%s"}""".formatted(list))).hasStatus(400);
        assertThat(send("POST", other, "/api/tasks", """
                {"title":"x","tagIds":["%s"]}""".formatted(tag))).hasStatus(400);
        assertThat(titles(todayView(other), "$.days[0].items")).isEmpty();

        // Excluir a lista desliga a tarefa, não a apaga.
        assertThat(send("DELETE", me, "/api/tasks/lists/" + list, null)).hasStatus(204);
        Map<String, Object> task = JsonPath.read(body(send("GET", me, "/api/tasks/" + id, null)), "$");
        assertThat(task.get("listId")).isNull();

        assertThat(mvc.get().uri("/api/tasks/agenda?from=%s&to=%s".formatted(today, today))).hasStatus(401);
    }

    @Test
    void manualOrder() {
        String me = registerAndGetBearer();
        String a = create(me, "{\"title\":\"A\",\"dueDate\":\"%s\"}".formatted(today));
        String b = create(me, "{\"title\":\"B\",\"dueDate\":\"%s\"}".formatted(today));
        String c = create(me, "{\"title\":\"C\",\"dueDate\":\"%s\"}".formatted(today));
        assertThat(titles(todayView(me), "$.days[0].items")).containsExactly("A", "B", "C");
        assertThat(send("PUT", me, "/api/tasks/order", """
                {"taskIds":["%s","%s","%s"]}""".formatted(c, a, b))).hasStatus(204);
        assertThat(titles(todayView(me), "$.days[0].items")).containsExactly("C", "A", "B");
    }
}
