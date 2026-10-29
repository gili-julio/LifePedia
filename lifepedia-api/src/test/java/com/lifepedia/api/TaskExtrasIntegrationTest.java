package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.task.TaskReminderJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Hábitos (sequência e %), lembretes e ligação com fixo do financeiro. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TaskExtrasIntegrationTest extends ApiTestSupport {

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    TaskReminderJob reminders;

    private String create(String bearer, String base, String json) {
        return JsonPath.read(post(bearer, base, json), "$.id");
    }

    private void status(String bearer, String taskId, LocalDate date, String status) {
        assertThat(send("PUT", bearer, "/api/tasks/" + taskId + "/status", """
                {"date":"%s","status":"%s"}""".formatted(date, status))).hasStatus(204);
    }

    private List<String> reminderTitles(String bearer) {
        return JsonPath.read(body(send("GET", bearer, "/api/notifications?size=50", null)),
                "$.items[?(@.type == 'TASK_REMINDER')].data.taskTitle");
    }

    @Test
    void habitStreakSkipsAreNeutral() {
        String alice = registerAndGetBearer();
        String id = create(alice, "/api/tasks", """
                {"title":"Ler","dueDate":"%s","habit":true,"repeat":{"unit":"DAY","interval":1}}""".formatted(today.minusDays(5)));
        status(alice, id, today.minusDays(5), "DONE");
        status(alice, id, today.minusDays(4), "DONE");
        status(alice, id, today.minusDays(2), "DONE");
        status(alice, id, today.minusDays(1), "SKIPPED");

        assertThat(send("GET", alice, "/api/tasks/habits", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.length()").isEqualTo(1);
            assertThat(json).extractingPath("$[0].streak").isEqualTo(1);
            assertThat(json).extractingPath("$[0].best").isEqualTo(2);
            assertThat(json).extractingPath("$[0].days.length()").isEqualTo(7);
            assertThat(json).extractingPath("$[0].days[0].state").isNull();
            assertThat(json).extractingPath("$[0].days[3].state").isEqualTo("MISSED");
            assertThat(json).extractingPath("$[0].days[5].state").isEqualTo("SKIPPED");
            assertThat(json).extractingPath("$[0].days[6].state").isEqualTo("PENDING");
        });
        status(alice, id, today, "DONE");
        assertThat(send("GET", alice, "/api/tasks/agenda?from=%s&to=%s".formatted(today, today), null)).bodyJson()
                .extractingPath("$.days[0].items[0].streak").isEqualTo(2);

        // Hábito precisa de repetição em dias fixos; tarefa comum não tem sequência.
        assertThat(send("POST", alice, "/api/tasks", """
                {"title":"X","dueDate":"%s","habit":true}""".formatted(today))).hasStatus(400);
        assertThat(send("POST", alice, "/api/tasks", """
                {"title":"X","dueDate":"%s","habit":true,"repeat":{"unit":"DAY","interval":2,"fromCompletion":true}}"""
                .formatted(today))).hasStatus(400);
        create(alice, "/api/tasks", """
                {"title":"Comum","dueDate":"%s","repeat":{"unit":"DAY","interval":1}}""".formatted(today));
        assertThat(send("GET", alice, "/api/tasks/agenda?from=%s&to=%s".formatted(today, today), null)).bodyJson()
                .extractingPath("$.days[0].items[?(@.title == 'Comum')].streak").asArray().containsExactly((Object) null);
        assertThat(send("GET", alice, "/api/tasks/habits", null)).bodyJson().extractingPath("$.length()").isEqualTo(1);
    }

    @Test
    void remindersPersonalGroupAndMoved() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        LocalDate day = today.plusDays(5);

        assertThat(send("POST", alice, "/api/tasks", """
                {"title":"X","dueDate":"%s","reminderMinutes":10}""".formatted(day))).hasStatus(400);
        String call = create(alice, "/api/tasks", """
                {"title":"Ligar","dueDate":"%s","dueTime":"10:00","reminderMinutes":15}""".formatted(day));
        assertThat(send("GET", alice, "/api/tasks/" + call, null)).bodyJson()
                .extractingPath("$.reminderMinutes").isEqualTo(15);

        LocalDateTime at = day.atTime(9, 45);
        assertThat(reminders.remind(at.minusMinutes(1), at)).isGreaterThanOrEqualTo(1);
        assertThat(reminderTitles(alice)).containsExactly("Ligar");
        assertThat(body(send("GET", alice, "/api/notifications", null))).contains("\"time\":\"10:00\"", "\"minutes\":\"15\"");
        reminders.remind(at, at.plusMinutes(1));
        assertThat(reminderTitles(alice)).hasSize(1);

        // Sem horário, o lembrete sai junto.
        assertThat(send("PUT", alice, "/api/tasks/" + call + "/move", """
                {"date":"%s","to":null}""".formatted(day))).hasStatus(204);
        assertThat(send("GET", alice, "/api/tasks/" + call, null)).bodyJson()
                .extractingPath("$.reminderMinutes").isNull();

        // Grupo: sem responsável avisa todos; com responsável, só ele.
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", bob, "/api/groups/join/" + token, null)).hasStatus2xxSuccessful();
        String base = "/api/groups/" + groupId + "/tasks";
        create(alice, base, """
                {"title":"Lixo","dueDate":"%s","dueTime":"11:00","reminderMinutes":0}""".formatted(day));
        create(alice, base, """
                {"title":"Louça","dueDate":"%s","dueTime":"11:00","reminderMinutes":0,"assignees":["%s"]}"""
                .formatted(day, userId(bob)));
        reminders.remind(day.atTime(10, 59), day.atTime(11, 0));
        assertThat(reminderTitles(alice)).containsExactlyInAnyOrder("Ligar", "Lixo");
        assertThat(reminderTitles(bob)).containsExactlyInAnyOrder("Lixo", "Louça");

        // Repetida com lembrete 1 dia antes: a adiada avisa no dia novo, não no antigo.
        String walk = create(alice, "/api/tasks", """
                {"title":"Caminhar","dueDate":"%s","dueTime":"08:00","reminderMinutes":1440,
                 "repeat":{"unit":"DAY","interval":1,"count":3}}""".formatted(day));
        assertThat(send("PUT", alice, "/api/tasks/" + walk + "/move", """
                {"date":"%s","to":"%s"}""".formatted(day.plusDays(1), day.plusDays(4)))).hasStatus(204);
        reminders.remind(day.atTime(7, 59), day.atTime(8, 0));
        assertThat(reminderTitles(alice).stream().filter("Caminhar"::equals)).isEmpty();
        reminders.remind(day.plusDays(3).atTime(7, 59), day.plusDays(3).atTime(8, 0));
        assertThat(reminderTitles(alice).stream().filter("Caminhar"::equals)).hasSize(1);
    }

    @Test
    void fixedLinkOnlyPersonalAndOwn() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String fixed = JsonPath.read(post(alice, "/api/finance/recurring", """
                {"description":"Luz","type":"EXPENSE","categoryId":"%s","amount":180,"startMonth":"%s"}"""
                .formatted(categoryId(alice, "EXPENSE", "Moradia"), YearMonth.now())), "$.id");

        String task = create(alice, "/api/tasks", """
                {"title":"Pagar luz","dueDate":"%s","recurringEntryId":"%s","repeat":{"unit":"MONTH","interval":1}}"""
                .formatted(today, fixed));
        assertThat(send("GET", alice, "/api/tasks/agenda?from=%s&to=%s".formatted(today, today), null)).bodyJson()
                .extractingPath("$.days[0].items[0].recurringEntryId").isEqualTo(fixed);
        assertThat(send("POST", bob, "/api/tasks", """
                {"title":"X","recurringEntryId":"%s"}""".formatted(fixed))).hasStatus(400);
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        assertThat(send("POST", alice, "/api/groups/" + groupId + "/tasks", """
                {"title":"X","recurringEntryId":"%s"}""".formatted(fixed))).hasStatus(400);

        // Fixo excluído: a tarefa continua, sem a ligação.
        assertThat(send("DELETE", alice, "/api/finance/recurring/" + fixed, null)).hasStatus(204);
        assertThat(send("GET", alice, "/api/tasks/" + task, null)).bodyJson()
                .extractingPath("$.recurringEntryId").isNull();
    }
}
