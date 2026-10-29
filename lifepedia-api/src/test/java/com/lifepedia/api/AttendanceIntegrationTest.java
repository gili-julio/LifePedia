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

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AttendanceIntegrationTest extends ApiTestSupport {

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Test
    void countsMarksAndLinkedTaskAndIsolatesUsers() {
        String alice = registerAndGetBearer();
        String task = id(post(alice, "/api/tasks", """
                {"title":"Aula de Cálculo","dueDate":"%s","dueTime":"08:00","repeat":{"unit":"DAY","interval":1}}"""
                .formatted(today.minusDays(6))));
        assertThat(send("PUT", alice, "/api/tasks/" + task + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(today.minusDays(5)))).hasStatus(204);
        assertThat(JsonPath.<List<String>>read(body(send("GET", alice, "/api/attendance/tasks", null)), "$[*].title"))
                .contains("Aula de Cálculo");

        // Período já encerrado (ontem): os 6 dias contam e nada depende do horário de agora.
        String created = post(alice, "/api/attendance", request("Cálculo", today.minusDays(6), today.minusDays(1), task));
        String id = id(created);
        assertThat(JsonPath.<Integer>read(created, "$.stats.presences")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(created, "$.stats.pending")).isEqualTo(5);
        assertThat(JsonPath.<String>read(created, "$.task.title")).isEqualTo("Aula de Cálculo");

        assertThat(mark(alice, id, today.minusDays(4), "\"ABSENT\"")).hasStatusOk();
        assertThat(mark(alice, id, today.minusDays(3), "\"CANCELED\"")).hasStatusOk();
        assertThat(mark(alice, id, today.minusDays(2), "\"PRESENT\"")).hasStatusOk();
        // Marcar falta vale mais que a tarefa concluída; tirar a marcação volta para a presença da tarefa.
        assertThat(JsonPath.<String>read(body(mark(alice, id, today.minusDays(5), "\"ABSENT\"")), "$.status"))
                .isEqualTo("ABSENT");
        String stats = body(send("GET", alice, "/api/attendance", null));
        assertThat(JsonPath.<Integer>read(stats, "$[0].stats.absences")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(stats, "$[0].stats.remaining")).isEqualTo(0);
        String back = body(mark(alice, id, today.minusDays(5), "null"));
        assertThat(JsonPath.<String>read(back, "$.status")).isEqualTo("PRESENT");
        assertThat(JsonPath.<Boolean>read(back, "$.fromTask")).isTrue();
        stats = body(send("GET", alice, "/api/attendance", null));
        assertThat(JsonPath.<Integer>read(stats, "$[0].stats.presences")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(stats, "$[0].stats.absences")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(stats, "$[0].stats.canceled")).isEqualTo(1);
        assertThat(JsonPath.<Integer>read(stats, "$[0].stats.pending")).isEqualTo(2);
        assertThat(JsonPath.<Integer>read(stats, "$[0].stats.remaining")).isEqualTo(1);

        String days = body(send("GET", alice, "/api/attendance/" + id + "/days", null));
        assertThat(JsonPath.<List<String>>read(days, "$[*].date")).hasSize(6).first().isEqualTo(today.minusDays(1).toString());
        // Fora do período.
        assertThat(mark(alice, id, today, "\"ABSENT\"")).hasStatus(400);

        String agenda = body(send("GET", alice, "/api/attendance/agenda?from=%s&to=%s"
                .formatted(today.minusDays(6), today.plusDays(6)), null));
        assertThat(JsonPath.<List<String>>read(agenda, "$[*].date")).hasSize(6);
        assertThat(JsonPath.<List<Boolean>>read(agenda, "$[?(@.date == '%s')].fromTask".formatted(today.minusDays(5))))
                .containsExactly(true);
        assertThat(JsonPath.<String>read(agenda, "$[0].taskId")).isEqualTo(task);
        assertThat(send("GET", alice, "/api/attendance/agenda?from=%s&to=%s"
                .formatted(today, today.plusDays(70)), null)).hasStatus(400);

        // Isolamento: outra pessoa não vê, não edita, não marca e não liga a tarefa da Alice.
        String bob = registerAndGetBearer();
        assertThat(JsonPath.<List<Object>>read(body(send("GET", bob, "/api/attendance", null)), "$")).isEmpty();
        assertThat(send("PUT", bob, "/api/attendance/" + id, request("X", today, null, null))).hasStatus(404);
        assertThat(mark(bob, id, today.minusDays(2), "\"ABSENT\"")).hasStatus(404);
        assertThat(send("GET", bob, "/api/attendance/" + id + "/days", null)).hasStatus(404);
        assertThat(send("POST", bob, "/api/attendance", request("X", today, null, task))).hasStatus(404);
        assertThat(send("DELETE", bob, "/api/attendance/" + id, null)).hasStatus(404);
        assertThat(mvc.get().uri("/api/attendance").exchange()).hasStatus(401);

        // Arquivo da conta leva os compromissos (só registro).
        String exported = body(send("GET", alice, "/api/account/export", null));
        assertThat(JsonPath.<List<String>>read(exported, "$.commitments[*].name")).containsExactly("Cálculo");
        assertThat(JsonPath.<List<Object>>read(exported, "$.commitments[0].marks")).hasSize(3);

        // Excluir a tarefa só desliga.
        assertThat(send("DELETE", alice, "/api/tasks/" + task, null)).hasStatus(204);
        assertThat(JsonPath.<Object>read(body(send("GET", alice, "/api/attendance", null)), "$[0].task")).isNull();
        assertThat(send("DELETE", alice, "/api/attendance/" + id, null)).hasStatus(204);
        assertThat(JsonPath.<List<Object>>read(body(send("GET", alice, "/api/attendance", null)), "$")).isEmpty();
    }

    @Test
    void validatesAndOnlyCancelsFutureDays() {
        String me = registerAndGetBearer();
        assertThat(send("POST", me, "/api/attendance", """
                {"name":"Inglês","color":"#3366ff","weekdays":[],"time":"19:00","durationMinutes":60,
                 "startDate":"%s","maxAbsences":5}""".formatted(today))).hasStatus(400);
        assertThat(send("POST", me, "/api/attendance", request("Inglês", today, today.minusDays(1), null))).hasStatus(400);
        assertThat(send("POST", me, "/api/attendance", request("Inglês", today.minusYears(6), null, null))).hasStatus(400);
        assertThat(send("POST", me, "/api/attendance", request("Inglês", today, null, null)
                .replace("#3366ff", "azul"))).hasStatus(400);

        String id = id(post(me, "/api/attendance", request("Inglês", today, null, null)));
        assertThat(mark(me, id, today.plusDays(2), "\"ABSENT\"")).hasStatus(400);
        assertThat(JsonPath.<String>read(body(mark(me, id, today.plusDays(2), "\"CANCELED\"")), "$.status"))
                .isEqualTo("CANCELED");
        String agenda = body(send("GET", me, "/api/attendance/agenda?from=%s&to=%s"
                .formatted(today.plusDays(1), today.plusDays(3)), null));
        assertThat(JsonPath.<List<String>>read(agenda, "$[*].status")).containsExactly("UPCOMING", "CANCELED", "UPCOMING");
        assertThat(mark(me, id, today.minusDays(1), "\"ABSENT\"")).hasStatus(400);
    }

    private MvcTestResult mark(String bearer, String id, LocalDate date, String status) {
        return send("PUT", bearer, "/api/attendance/" + id + "/days/" + date, """
                {"status":%s}""".formatted(status));
    }

    private static String request(String name, LocalDate start, LocalDate end, String task) {
        return """
                {"name":"%s","color":"#3366ff","weekdays":[1,2,3,4,5,6,7],"time":"08:00","durationMinutes":100,
                 "startDate":"%s","endDate":%s,"maxAbsences":2,"taskId":%s}"""
                .formatted(name, start, end == null ? "null" : "\"" + end + "\"", task == null ? "null" : "\"" + task + "\"");
    }

    private static String id(String json) {
        return JsonPath.read(json, "$.id");
    }
}
