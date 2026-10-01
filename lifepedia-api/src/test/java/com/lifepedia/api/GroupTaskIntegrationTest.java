package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.task.TaskOverdueJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class GroupTaskIntegrationTest extends ApiTestSupport {

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    TaskOverdueJob overdueJob;

    /** Grupo da Alice (admin) com Bob e Carol. */
    private record Team(String alice, String bob, String carol, String aliceId, String bobId, String carolId,
                        String groupId, String base) {
    }

    private Team team() {
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String carol = registerAndGetBearer("Carol", newUsername());
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", bob, "/api/groups/join/" + token, null)).hasStatus2xxSuccessful();
        assertThat(send("POST", carol, "/api/groups/join/" + token, null)).hasStatus2xxSuccessful();
        return new Team(alice, bob, carol, userId(alice), userId(bob), userId(carol), groupId,
                "/api/groups/" + groupId + "/tasks");
    }

    private String agenda(String bearer, String base, LocalDate from, LocalDate to, String extra) {
        MvcTestResult result = send("GET", bearer, base + "/agenda?from=%s&to=%s%s".formatted(from, to, extra), null);
        assertThat(result).hasStatusOk();
        return body(result);
    }

    /** Tipos e títulos das notificações, das mais recentes para as mais antigas. */
    private List<Map<String, Object>> notifications(String bearer) {
        return JsonPath.read(body(send("GET", bearer, "/api/notifications?size=50", null)), "$.items[*]");
    }

    private List<String> types(String bearer) {
        return notifications(bearer).stream().map(n -> (String) n.get("type")).toList();
    }

    private String create(String bearer, String base, String json) {
        return JsonPath.read(post(bearer, base, json), "$.id");
    }

    @Test
    void assigneeAccessPermissionsAndPersonalView() {
        Team t = team();
        String outsider = registerAndGetBearer();

        // Só membros; sem token, 401.
        assertThat(send("GET", outsider, t.base() + "/agenda?from=%s&to=%s".formatted(today, today), null)).hasStatus(404);
        assertThat(mvc.get().uri(t.base() + "/labels").exchange()).hasStatus(401);

        // Validação do responsável.
        assertThat(send("POST", t.alice(), t.base(), """
                {"title":"X","assigneeId":"%s"}""".formatted(userId(outsider)))).hasStatus(400);
        assertThat(send("POST", t.alice(), t.base(), """
                {"title":"X","rotation":["%s","%s"]}""".formatted(t.aliceId(), t.bobId()))).hasStatus(400);
        assertThat(send("POST", t.alice(), t.base(), """
                {"title":"X","dueDate":"%s","repeat":{"unit":"DAY","interval":1},"rotation":["%s"]}"""
                .formatted(today, t.aliceId()))).hasStatus(400);
        assertThat(send("POST", t.alice(), "/api/tasks", """
                {"title":"X","assigneeId":"%s"}""".formatted(t.aliceId()))).hasStatus(400);

        // Alice cria para o Bob: Bob é avisado e vê na visão pessoal; Carol não.
        String dishes = create(t.alice(), t.base(), """
                {"title":"Lavar louça","dueDate":"%s","assigneeId":"%s"}""".formatted(today, t.bobId()));
        assertThat(types(t.bob())).containsExactly("TASK_ASSIGNED");
        String mine = agenda(t.bob(), "/api/tasks", today, today, "&overdue=true");
        assertThat(JsonPath.<List<String>>read(mine, "$.days[0].items[*].title")).containsExactly("Lavar louça");
        assertThat(JsonPath.<String>read(mine, "$.days[0].items[0].groupId")).isEqualTo(t.groupId());
        assertThat(JsonPath.<Boolean>read(mine, "$.days[0].items[0].canDelete")).isFalse();
        assertThat(JsonPath.<List<String>>read(agenda(t.carol(), "/api/tasks", today, today, ""), "$.days[0].items")).isEmpty();
        // Com filtro de lista a visão pessoal fica só nas pessoais.
        String list = JsonPath.read(post(t.bob(), "/api/tasks/lists", """
                {"name":"Casa","color":"#112233"}"""), "$.id");
        assertThat(JsonPath.<List<String>>read(agenda(t.bob(), "/api/tasks", today, today, "&list=" + list), "$.days[0].items")).isEmpty();
        // Tarefa de grupo não responde pela rota pessoal.
        assertThat(send("GET", t.bob(), "/api/tasks/" + dishes, null)).hasStatus(404);

        // Carol conclui a tarefa do Bob: Bob é avisado.
        assertThat(send("PUT", t.carol(), t.base() + "/" + dishes + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(today))).hasStatus(204);
        assertThat(types(t.bob())).containsExactly("TASK_COMPLETED", "TASK_ASSIGNED");
        Map<String, Object> done = notifications(t.bob()).getFirst();
        assertThat(JsonPath.<String>read(done, "$.data.actorName")).isEqualTo("Carol");
        assertThat(JsonPath.<String>read(done, "$.data.taskTitle")).isEqualTo("Lavar louça");
        String history = body(send("GET", t.alice(), t.base() + "/history", null));
        assertThat(JsonPath.<String>read(history, "$.items[0].completedBy")).isEqualTo(t.carolId());

        // Qualquer membro edita; excluir só quem criou ou admin.
        String bobTask = create(t.bob(), t.base(), """
                {"title":"Comprar pão"}""");
        assertThat(send("PUT", t.carol(), t.base() + "/" + bobTask, """
                {"title":"Comprar pão integral"}""")).hasStatusOk();
        assertThat(send("DELETE", t.carol(), t.base() + "/" + bobTask, null)).hasStatus(403);
        assertThat(send("DELETE", t.bob(), t.base() + "/" + dishes, null)).hasStatus(403);
        assertThat(JsonPath.<Boolean>read(body(send("GET", t.bob(), t.base() + "/" + bobTask, null)), "$.canDelete")).isTrue();
        assertThat(send("DELETE", t.alice(), t.base() + "/" + bobTask, null)).hasStatus(204);
        assertThat(send("DELETE", t.alice(), t.base() + "/" + dishes, null)).hasStatus(204);

        // Listas do grupo separadas das pessoais.
        assertThat(send("POST", t.carol(), t.base() + "/lists", """
                {"name":"Casa","color":"#112233"}""")).hasStatus(201);
        assertThat(send("POST", t.alice(), t.base(), """
                {"title":"Y","listId":"%s"}""".formatted(list))).hasStatus(400);
    }

    @Test
    void rotationTurnsSwapsCommentsAndLeaving() {
        Team t = team();
        String trash = create(t.alice(), t.base(), """
                {"title":"Lixo","dueDate":"%s","repeat":{"unit":"DAY","interval":1},"rotation":["%s","%s","%s"]}"""
                .formatted(today, t.aliceId(), t.bobId(), t.carolId()));
        // A primeira vez é da Alice (quem criou): ninguém é avisado.
        assertThat(types(t.bob())).isEmpty();
        String week = agenda(t.alice(), t.base(), today, today.plusDays(3), "");
        assertThat(JsonPath.<List<String>>read(week, "$.days[*].items[0].assigneeId"))
                .containsExactly(t.aliceId(), t.bobId(), t.carolId(), t.aliceId());
        assertThat(JsonPath.<Boolean>read(week, "$.days[0].items[0].rotating")).isTrue();
        assertThat(JsonPath.<List<String>>read(agenda(t.alice(), t.base(), today, today.plusDays(3), "&assignee=" + t.bobId()),
                "$.days[*].items[*].date")).containsExactly(today.plusDays(1).toString());
        assertThat(JsonPath.<List<String>>read(agenda(t.bob(), "/api/tasks", today, today.plusDays(3), ""),
                "$.days[*].items[*].date")).containsExactly(today.plusDays(1).toString());

        // Alice conclui: a vez passa para o Bob.
        assertThat(send("PUT", t.alice(), t.base() + "/" + trash + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(today))).hasStatus(204);
        assertThat(types(t.bob())).containsExactly("TASK_TURN");
        assertThat(JsonPath.<String>read(notifications(t.bob()).getFirst(), "$.data.date")).isEqualTo(today.plusDays(1).toString());

        // Bob passa a vez de depois de amanhã (da Carol) para a Alice.
        assertThat(send("PUT", t.bob(), t.base() + "/" + trash + "/assignee", """
                {"date":"%s","assigneeId":"%s"}""".formatted(today.plusDays(2), t.aliceId()))).hasStatus(204);
        assertThat(types(t.alice())).containsExactly("TASK_ASSIGNED");
        assertThat(send("PUT", t.bob(), t.base() + "/" + trash + "/assignee", """
                {"date":"%s","assigneeId":"%s"}""".formatted(today.plusDays(2), userId(registerAndGetBearer())))).hasStatus(400);
        week = agenda(t.alice(), t.base(), today, today.plusDays(3), "");
        assertThat(JsonPath.<List<String>>read(week, "$.days[*].items[0].assigneeId"))
                .containsExactly(t.aliceId(), t.bobId(), t.aliceId(), t.aliceId());

        // Comentários: avisa quem criou e quem está no rodízio (menos quem escreveu).
        String comments = t.base() + "/" + trash + "/comments";
        String bobComment = JsonPath.read(post(t.bob(), comments, """
                {"body":"Hoje tem reciclável"}"""), "$.id");
        assertThat(types(t.carol())).containsExactly("TASK_COMMENT");
        assertThat(JsonPath.<String>read(notifications(t.carol()).getFirst(), "$.data.preview")).isEqualTo("Hoje tem reciclável");
        assertThat(types(t.bob())).doesNotContain("TASK_COMMENT");
        assertThat(send("POST", t.carol(), comments, """
                {"body":"  "}""")).hasStatus(400);
        post(t.carol(), comments, """
                {"body":"Ok!"}""");
        String page = body(send("GET", t.alice(), comments, null));
        assertThat(JsonPath.<List<String>>read(page, "$.items[*].body")).containsExactly("Ok!", "Hoje tem reciclável");
        assertThat(JsonPath.<String>read(page, "$.items[1].author.name")).isEqualTo("Bob");
        assertThat(JsonPath.<Integer>read(agenda(t.alice(), t.base(), today, today, ""), "$.days[0].items[0].commentCount")).isEqualTo(2);
        assertThat(send("DELETE", t.carol(), comments + "/" + bobComment, null)).hasStatus(403);
        assertThat(send("DELETE", t.alice(), comments + "/" + bobComment, null)).hasStatus(204);
        String personal = create(t.alice(), "/api/tasks", """
                {"title":"Minha"}""");
        assertThat(send("POST", t.alice(), "/api/tasks/" + personal + "/comments", """
                {"body":"oi"}""")).hasStatus(400);

        // Carol sai: deixa o rodízio e leva os comentários dela.
        assertThat(send("POST", t.carol(), "/api/groups/" + t.groupId() + "/leave", null)).hasStatus2xxSuccessful();
        String task = body(send("GET", t.alice(), t.base() + "/" + trash, null));
        assertThat(JsonPath.<List<String>>read(task, "$.rotation")).containsExactly(t.aliceId(), t.bobId());
        assertThat(JsonPath.<List<String>>read(body(send("GET", t.alice(), comments, null)), "$.items")).isEmpty();
        assertThat(JsonPath.<List<String>>read(agenda(t.alice(), t.base(), today.plusDays(1), today.plusDays(5), ""),
                "$.days[*].items[0].assigneeId")).doesNotContain(t.carolId());
    }

    @Test
    void rotationFromCompletionAndOverdueNotice() {
        Team t = team();
        String plants = create(t.alice(), t.base(), """
                {"title":"Regar plantas","dueDate":"%s","repeat":{"unit":"DAY","interval":2,"fromCompletion":true},
                 "rotation":["%s","%s"]}""".formatted(today, t.aliceId(), t.bobId()));
        assertThat(send("PUT", t.alice(), t.base() + "/" + plants + "/status", """
                {"date":"%s","status":"DONE"}""".formatted(today))).hasStatus(204);
        String next = agenda(t.alice(), t.base(), today.plusDays(2), today.plusDays(2), "");
        assertThat(JsonPath.<String>read(next, "$.days[0].items[0].assigneeId")).isEqualTo(t.bobId());
        assertThat(types(t.bob())).containsExactly("TASK_TURN");
        // Desfazer volta a vez para a Alice.
        assertThat(send("PUT", t.alice(), t.base() + "/" + plants + "/status", """
                {"date":"%s","status":"PENDING"}""".formatted(today))).hasStatus(204);
        assertThat(JsonPath.<String>read(agenda(t.alice(), t.base(), today, today, ""), "$.days[0].items[0].assigneeId"))
                .isEqualTo(t.aliceId());

        // Aviso de atrasada: responsável (ou quem criou, se não houver).
        create(t.alice(), t.base(), """
                {"title":"Pagar conta de luz","dueDate":"%s","assigneeId":"%s"}""".formatted(today.minusDays(1), t.bobId()));
        create(t.carol(), t.base(), """
                {"title":"Trocar lâmpada","dueDate":"%s"}""".formatted(today.minusDays(1)));
        overdueJob.notifyOverdue(today.minusDays(1));
        assertThat(types(t.bob()).getFirst()).isEqualTo("TASK_OVERDUE");
        assertThat(JsonPath.<String>read(notifications(t.bob()).getFirst(), "$.data.taskTitle")).isEqualTo("Pagar conta de luz");
        assertThat(types(t.carol())).containsExactly("TASK_OVERDUE");
        assertThat(types(t.alice())).doesNotContain("TASK_OVERDUE");
    }
}
