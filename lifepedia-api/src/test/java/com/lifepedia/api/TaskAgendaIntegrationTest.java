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
class TaskAgendaIntegrationTest extends ApiTestSupport {

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    /** Grupo da Alice com Bob e Carol. */
    private record Team(String alice, String bob, String carol, String aliceId, String bobId, String carolId,
                        String groupId, String base) {
    }

    @Test
    void severalAssigneesEachCompletesTheirPart() {
        Team t = team();
        String gym = id(post(t.alice(), t.base(), """
                {"title":"Academia","dueDate":"%s","dueTime":"07:00","assignees":["%s","%s"],"completion":"EACH"}"""
                .formatted(today, t.aliceId(), t.bobId())));
        assertThat(JsonPath.<List<String>>read(body(send("GET", t.bob(), t.base() + "/" + gym, null)), "$.assignees"))
                .containsExactlyInAnyOrder(t.aliceId(), t.bobId());
        // Aparece na tela pessoal dos dois, não na da Carol.
        assertThat(titles(agenda(t.bob(), "/api/tasks"))).containsExactly("Academia");
        assertThat(titles(agenda(t.carol(), "/api/tasks"))).isEmpty();

        // Bob faz a parte dele: continua pendente até a Alice fazer a dela.
        assertThat(status(t.bob(), t.base(), gym, "DONE")).hasStatus(204);
        String day = agenda(t.alice(), t.base());
        assertThat(JsonPath.<String>read(day, "$.days[0].items[0].status")).isEqualTo("PENDING");
        assertThat(JsonPath.<List<String>>read(day, "$.days[0].items[0].doneBy")).containsExactly(t.bobId());
        assertThat(status(t.alice(), t.base(), gym, "DONE")).hasStatus(204);
        assertThat(JsonPath.<String>read(agenda(t.alice(), t.base()), "$.days[0].items[0].status")).isEqualTo("DONE");

        // Bob desfaz: volta a pendente e só a parte da Alice fica.
        assertThat(status(t.bob(), t.base(), gym, "PENDING")).hasStatus(204);
        day = agenda(t.alice(), t.base());
        assertThat(JsonPath.<String>read(day, "$.days[0].items[0].status")).isEqualTo("PENDING");
        assertThat(JsonPath.<List<String>>read(day, "$.days[0].items[0].doneBy")).containsExactly(t.aliceId());

        // Basta um: a conclusão do Bob fecha para todos.
        String dishes = id(post(t.alice(), t.base(), """
                {"title":"Louça","dueDate":"%s","assignees":["%s","%s"]}""".formatted(today, t.aliceId(), t.bobId())));
        assertThat(status(t.bob(), t.base(), dishes, "DONE")).hasStatus(204);
        assertThat(JsonPath.<List<String>>read(agenda(t.alice(), t.base()), "$.days[0].items[?(@.title == 'Louça')].status"))
                .containsExactly("DONE");
    }

    @Test
    void allMembersAndValidation() {
        Team t = team();
        assertThat(send("POST", t.alice(), t.base(), """
                {"title":"X","allMembers":true,"assignees":["%s"]}""".formatted(t.bobId()))).hasStatus(400);
        String meeting = id(post(t.alice(), t.base(), """
                {"title":"Reunião","dueDate":"%s","allMembers":true,"completion":"EACH"}""".formatted(today)));
        String day = agenda(t.carol(), "/api/tasks");
        assertThat(titles(day)).containsExactly("Reunião");
        assertThat(JsonPath.<List<String>>read(day, "$.days[0].items[0].assignees"))
                .containsExactly(t.aliceId(), t.bobId(), t.carolId());
        assertThat(JsonPath.<Boolean>read(day, "$.days[0].items[0].allMembers")).isTrue();
        assertThat(JsonPath.<String>read(body(send("GET", t.alice(), t.base() + "/" + meeting, null)), "$.completion"))
                .isEqualTo("EACH");
        // "Cada um" com uma pessoa só vira "basta um".
        String solo = id(post(t.alice(), t.base(), """
                {"title":"Solo","assignees":["%s"],"completion":"EACH"}""".formatted(t.bobId())));
        assertThat(JsonPath.<String>read(body(send("GET", t.alice(), t.base() + "/" + solo, null)), "$.completion"))
                .isEqualTo("ANY");
    }

    @Test
    void personalTaskMovesToGroup() {
        Team t = team();
        String list = id(post(t.bob(), "/api/tasks/lists", """
                {"name":"Casa","color":"#112233"}"""));
        String task = id(post(t.bob(), "/api/tasks", """
                {"title":"Consertar a pia","dueDate":"%s","listId":"%s"}""".formatted(today, list)));
        String outsider = registerAndGetBearer();

        assertThat(send("PUT", outsider, "/api/tasks/" + task + "/group", """
                {"groupId":"%s"}""".formatted(t.groupId()))).hasStatus(404);
        String otherGroup = id(post(outsider, "/api/groups", """
                {"name":"Outro"}"""));
        assertThat(send("PUT", t.bob(), "/api/tasks/" + task + "/group", """
                {"groupId":"%s"}""".formatted(otherGroup))).hasStatus(404);

        String moved = body(send("PUT", t.bob(), "/api/tasks/" + task + "/group", """
                {"groupId":"%s"}""".formatted(t.groupId())));
        assertThat(JsonPath.<String>read(moved, "$.groupId")).isEqualTo(t.groupId());
        assertThat(JsonPath.<List<String>>read(moved, "$.assignees")).containsExactly(t.bobId());
        assertThat(JsonPath.<String>read(moved, "$.listId")).isNull();
        assertThat(send("GET", t.bob(), "/api/tasks/" + task, null)).hasStatus(404);
        assertThat(send("GET", t.carol(), t.base() + "/" + task, null)).hasStatusOk();
        // Continua na tela pessoal do Bob (ele é o responsável).
        assertThat(JsonPath.<List<String>>read(agenda(t.bob(), "/api/tasks"), "$.days[0].items[*].groupId"))
                .containsExactly(t.groupId());
        assertThat(send("PUT", t.bob(), t.base() + "/" + task + "/group", """
                {"groupId":"%s"}""".formatted(t.groupId()))).hasStatus(400);
    }

    @Test
    void conflictsAndAgendaSettings() {
        Team t = team();
        post(t.alice(), "/api/tasks", """
                {"title":"Dentista","dueDate":"%s","dueTime":"09:00","durationMinutes":60}""".formatted(today));
        post(t.alice(), t.base(), """
                {"title":"Mercado","dueDate":"%s","dueTime":"09:30","assignees":["%s"]}""".formatted(today, t.aliceId()));
        post(t.alice(), t.base(), """
                {"title":"Lixo","dueDate":"%s","dueTime":"09:40","assignees":["%s"]}""".formatted(today, t.carolId()));

        // Pessoal: a agenda da Alice inteira (inclui a do grupo em que ela é responsável).
        String mine = body(send("GET", t.alice(), "/api/tasks/conflicts?date=%s&time=09:15".formatted(today), null));
        assertThat(JsonPath.<List<String>>read(mine, "$[*].title")).containsExactlyInAnyOrder("Dentista", "Mercado");
        assertThat(JsonPath.<List<String>>read(body(send("GET", t.alice(),
                "/api/tasks/conflicts?date=%s&time=11:00".formatted(today), null)), "$")).isEmpty();

        // Grupo: só as pessoas consultadas; a agenda pessoal entra só para quem consulta.
        String bob = body(send("GET", t.bob(), t.base() + "/conflicts?date=%s&time=09:30&duration=30&people=%s"
                .formatted(today, t.carolId()), null));
        assertThat(JsonPath.<List<String>>read(bob, "$[*].title")).containsExactly("Lixo");
        assertThat(JsonPath.<List<String>>read(bob, "$[0].people")).containsExactly(t.carolId());
        String alice = body(send("GET", t.alice(), t.base() + "/conflicts?date=%s&time=09:15&people=%s"
                .formatted(today, t.aliceId()), null));
        assertThat(JsonPath.<List<String>>read(alice, "$[*].title")).containsExactlyInAnyOrder("Dentista", "Mercado");
        assertThat(send("GET", t.bob(), t.base() + "/conflicts?date=%s&time=09:00&duration=0".formatted(today), null))
                .hasStatus(400);

        // Horas visíveis da agenda.
        String settings = body(send("GET", t.alice(), "/api/tasks/agenda-settings", null));
        assertThat(JsonPath.<Integer>read(settings, "$.dayStart")).isEqualTo(6);
        assertThat(send("PUT", t.alice(), "/api/tasks/agenda-settings", """
                {"dayStart":7,"dayEnd":22}""")).hasStatusOk();
        assertThat(JsonPath.<Integer>read(body(send("GET", t.alice(), "/api/tasks/agenda-settings", null)), "$.dayEnd"))
                .isEqualTo(22);
        assertThat(JsonPath.<Integer>read(body(send("GET", t.bob(), "/api/tasks/agenda-settings", null)), "$.dayEnd"))
                .isEqualTo(23);
        assertThat(JsonPath.<List<Integer>>read(settings, "$.sleepMinutes")).containsOnly(480).hasSize(7);
        assertThat(send("PUT", t.alice(), "/api/tasks/agenda-settings", """
                {"dayStart":7,"dayEnd":22,"sleepMinutes":[420,420,420,420,450,540,600]}""")).hasStatusOk();
        // Sem o sono no corpo (só as horas visíveis): mantém o salvo.
        assertThat(send("PUT", t.alice(), "/api/tasks/agenda-settings", """
                {"dayStart":7,"dayEnd":22}""")).hasStatusOk();
        assertThat(JsonPath.<List<Integer>>read(body(send("GET", t.alice(), "/api/tasks/agenda-settings", null)),
                "$.sleepMinutes")).containsExactly(420, 420, 420, 420, 450, 540, 600);
        assertThat(send("PUT", t.alice(), "/api/tasks/agenda-settings", """
                {"dayStart":7,"dayEnd":22,"sleepMinutes":[420,420]}""")).hasStatus(400);
        assertThat(send("PUT", t.alice(), "/api/tasks/agenda-settings", """
                {"dayStart":7,"dayEnd":22,"sleepMinutes":[420,420,420,420,420,420,1000]}""")).hasStatus(400);
        assertThat(send("PUT", t.alice(), "/api/tasks/agenda-settings", """
                {"dayStart":10,"dayEnd":12}""")).hasStatus(400);
        assertThat(send("PUT", t.alice(), "/api/tasks/agenda-settings", """
                {"dayStart":-1,"dayEnd":12}""")).hasStatus(400);
        assertThat(mvc.get().uri("/api/tasks/agenda-settings").exchange()).hasStatus(401);
    }

    @Test
    void historyCountsPerDayAcrossPages() {
        String bearer = registerAndGetBearer();
        for (String title : List.of("Um", "Dois", "Três")) {
            String id = id(post(bearer, "/api/tasks", """
                    {"title":"%s","dueDate":"%s"}""".formatted(title, today)));
            assertThat(status(bearer, "/api/tasks", id, "DONE")).hasStatus(204);
        }
        // Página de 2: o total do dia continua 3.
        String page = body(send("GET", bearer, "/api/tasks/history?size=2", null));
        assertThat(JsonPath.<List<Integer>>read(page, "$.items[*].dayCount")).containsExactly(3, 3);
        assertThat(JsonPath.<List<String>>read(page, "$.items[*].completedDay")).containsOnly(today.toString());
    }

    private Team team() {
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String carol = registerAndGetBearer("Carol", newUsername());
        String groupId = id(post(alice, "/api/groups", """
                {"name":"Casa"}"""));
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", bob, "/api/groups/join/" + token, null)).hasStatus2xxSuccessful();
        assertThat(send("POST", carol, "/api/groups/join/" + token, null)).hasStatus2xxSuccessful();
        return new Team(alice, bob, carol, userId(alice), userId(bob), userId(carol), groupId,
                "/api/groups/" + groupId + "/tasks");
    }

    private String agenda(String bearer, String base) {
        MvcTestResult result = send("GET", bearer, base + "/agenda?from=%s&to=%s".formatted(today, today), null);
        assertThat(result).hasStatusOk();
        return body(result);
    }

    private MvcTestResult status(String bearer, String base, String task, String status) {
        return send("PUT", bearer, base + "/" + task + "/status", """
                {"date":"%s","status":"%s"}""".formatted(today, status));
    }

    private static List<String> titles(String agenda) {
        return JsonPath.read(agenda, "$.days[0].items[*].title");
    }

    private static String id(String json) {
        return JsonPath.read(json, "$.id");
    }
}
