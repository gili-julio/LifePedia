package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.school.SchoolService;
import com.lifepedia.api.shared.ai.GeminiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Escola: vínculos, importação do histórico, notas, avaliações com tarefa, frequência, risco, grupo, IA e isolamento. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SchoolIntegrationTest extends ApiTestSupport {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String ENROLLMENT = """
            {"kind":"UNDERGRADUATE","course":"Engenharia de Software","institution":"UFRN","active":true,"color":"#2563EB",
             "rules":{"scale":"NUMERIC","passAverage":6,"lowAverage":5,"minUnit":3,"minAttendance":75},
             "required":{"mandatory":300,"optional":120,"complementary":30},"complementaryDone":10}""";

    @MockitoBean
    GeminiClient gemini;

    @Autowired
    SchoolService school;

    @Autowired
    JdbcClient jdbc;

    @Test
    void importGradesAssessmentsAndRisk() throws Exception {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        LocalDate today = LocalDate.now(ZONE);
        String commitment = JsonPath.read(post(alice, "/api/attendance", """
                {"name":"DIM0517 Gerência","color":"#3366ff","weekdays":[1,2,3,4,5,6,7],"time":"08:00","durationMinutes":100,
                 "startDate":"%s","endDate":"%s","maxAbsences":2,"taskId":null}""".formatted(today.minusDays(3), today.plusDays(30))),
                "$.id");

        String enrollment = JsonPath.read(post(alice, "/api/school/enrollments", ENROLLMENT), "$.id");
        assertThat(send("POST", alice, "/api/school/enrollments", ENROLLMENT.replace("\"lowAverage\":5", "\"lowAverage\":7")))
                .hasStatus(400);
        assertThat(send("POST", alice, "/api/school/enrollments", ENROLLMENT.replace("Engenharia de Software", ""))).hasStatus(400);

        String history = """
                {"courses":[
                  {"period":null,"code":"DIM0124","name":"PROGRAMAÇÃO CONCORRENTE","hours":60,"category":"MANDATORY","status":"CUMP"},
                  {"period":"2025.2","code":"IMD0030","name":"LINGUAGEM DE PROGRAMAÇÃO I","hours":60,"category":"MANDATORY",
                   "status":"APR","attendance":95,"average":8},
                  {"period":"2025.2","code":"IMD0405","name":"FUNDAMENTOS DE SI","hours":60,"category":"OPTIONAL","status":"APR",
                   "average":10},
                  {"period":"2026.2","code":"DIM0517","name":"GERÊNCIA DE CONFIGURAÇÃO E MUDANÇAS","hours":60,"classCode":"01",
                   "category":"MANDATORY","status":"MATR","teacher":"Dr. Fulano (60h)"}],
                 "required":{"mandatory":2870,"optional":540,"complementary":190},"dryRun":%s}""";
        String preview = body(send("POST", alice, "/api/school/enrollments/" + enrollment + "/import", history.formatted("true")));
        assertThat(JsonPath.<Integer>read(preview, "$.created")).isEqualTo(4);
        assertThat(JsonPath.<List<Object>>read(body(send("GET", alice, "/api/school/enrollments/" + enrollment, null)), "$.courses"))
                .isEmpty();
        send("POST", alice, "/api/school/enrollments/" + enrollment + "/import", history.formatted("false"));
        String again = body(send("POST", alice, "/api/school/enrollments/" + enrollment + "/import", history.formatted("false")));
        assertThat(JsonPath.<Integer>read(again, "$.updated")).isEqualTo(4);
        assertThat(JsonPath.<Integer>read(again, "$.created")).isZero();

        String detail = body(send("GET", alice, "/api/school/enrollments/" + enrollment, null));
        assertThat(JsonPath.<String>read(detail, "$.enrollment.currentPeriod")).isEqualTo("2026.2");
        assertThat(JsonPath.<Double>read(detail, "$.enrollment.index")).isEqualTo(9.0);
        assertThat(JsonPath.<Integer>read(detail, "$.enrollment.required.mandatory")).isEqualTo(2870);
        assertThat(JsonPath.<Integer>read(detail, "$.progress.mandatoryDone")).isEqualTo(120);
        assertThat(JsonPath.<Integer>read(detail, "$.progress.ongoing")).isEqualTo(60);
        assertThat(JsonPath.<List<String>>read(detail, "$.periods[*].period")).containsExactly("2025.2", "2026.2");
        String course = JsonPath.<List<String>>read(detail, "$.courses[?(@.code == 'DIM0517')].id").getFirst();
        assertThat(JsonPath.<List<String>>read(detail, "$.courses[?(@.code == 'DIM0517')].attendance.commitmentId"))
                .containsExactly(commitment);
        assertThat(JsonPath.<List<String>>read(detail, "$.courses[?(@.code == 'DIM0517')].units[*].label"))
                .containsExactly("Unid. 1", "Unid. 2", "Unid. 3");

        // Notas coladas do SIGAA: média parcial abaixo de 6 avisa uma vez; subir a nota libera o aviso.
        String grades = """
                {"units":[{"label":"Unid. 1","grade":%s},{"label":"Unid. 2","grade":null},{"label":"Unid. 3","grade":null}],
                 "recovery":null,"absences":0}""";
        String low = body(send("PUT", alice, "/api/school/courses/" + course + "/grades", grades.formatted("4.5")));
        assertThat(JsonPath.<Double>read(low, "$.course.result.partial")).isEqualTo(4.5);
        assertThat(JsonPath.<Boolean>read(low, "$.course.result.gradeRisk")).isTrue();
        send("PUT", alice, "/api/school/courses/" + course + "/grades", grades.formatted("4"));
        assertThat(types(alice).stream().filter("SCHOOL_RISK"::equals)).hasSize(1);
        assertThat(send("PUT", alice, "/api/school/courses/" + course + "/grades", grades.formatted("11"))).hasStatus(400);

        // Avaliação com nota entra na unidade e vira tarefa com lembrete.
        String exam = """
                {"title":"Prova 1","kind":"EXAM","dueDate":"%s","dueTime":"10:00","unit":1,"weight":1,"grade":%s,
                 "createTask":true,"reminderMinutes":60}""";
        String withExam = body(send("POST", alice, "/api/school/courses/" + course + "/assessments",
                exam.formatted(today.plusDays(2), "9")));
        assertThat(JsonPath.<Double>read(withExam, "$.course.units[0].grade")).isEqualTo(9.0);
        assertThat(JsonPath.<Boolean>read(withExam, "$.course.units[0].fromAssessments")).isTrue();
        assertThat(JsonPath.<String>read(withExam, "$.course.next.title")).isEqualTo("Prova 1");
        assertThat(jdbc.sql("select title from tasks where user_id = :u and reminder_minutes = 60")
                .param("u", UUID.fromString(userId(alice))).query(String.class).list())
                .containsExactly("Prova 1 - GERÊNCIA DE CONFIGURAÇÃO E MUDANÇAS");
        assertThat(school.checkRisks()).isZero();
        assertThat(send("POST", alice, "/api/school/courses/" + course + "/assessments", exam.formatted(today, "12"))).hasStatus(400);

        // Pasta de notas, links e grupo.
        String folder = JsonPath.read(body(send("POST", alice, "/api/school/courses/" + course + "/notes-folder", null)),
                "$.course.noteFolderId");
        assertThat(JsonPath.<String>read(body(send("POST", alice, "/api/school/courses/" + course + "/notes-folder", null)),
                "$.course.noteFolderId")).isEqualTo(folder);
        String groupId = JsonPath.read(post(alice, "/api/groups", "{\"name\":\"Turma\"}"), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", bob, "/api/groups/join/" + token, null)).hasStatus2xxSuccessful();
        String update = """
                {"period":"2026.2","code":"DIM0517","name":"GERÊNCIA DE CONFIGURAÇÃO E MUDANÇAS","hours":60,"classCode":"01",
                 "category":"MANDATORY","status":"MATR","teacher":"Fulano","teacherEmail":"fulano@ufrn.br","room":"A3",
                 "links":[{"label":"Discord","url":"https://discord.gg/turma","kind":"DISCORD"}],
                 "commitmentId":"%s","groupId":"%s"}""";
        assertThat(send("PUT", alice, "/api/school/courses/" + course, update.formatted(commitment, groupId))).hasStatusOk();
        assertThat(send("PUT", alice, "/api/school/courses/" + course,
                update.formatted(commitment, groupId).replace("https://discord.gg/turma", "javascript:alert(1)"))).hasStatus(400);
        String shared = body(send("GET", bob, "/api/school", null));
        assertThat(JsonPath.<List<String>>read(shared, "$.shared[*].links[*].url")).containsExactly("https://discord.gg/turma");
        assertThat(JsonPath.<List<String>>read(shared, "$.shared[*].upcoming[*].title")).containsExactly("Prova 1");
        assertThat(shared).doesNotContain("\"grade\"", "\"average\"");

        // Isolamento: Bob não vê nem mexe nas coisas da Alice.
        assertThat(send("GET", bob, "/api/school/courses/" + course, null)).hasStatus(404);
        assertThat(send("PUT", bob, "/api/school/courses/" + course + "/grades", grades.formatted("10"))).hasStatus(404);
        assertThat(send("GET", bob, "/api/school/enrollments/" + enrollment, null)).hasStatus(404);
        assertThat(send("POST", bob, "/api/school/enrollments/" + enrollment + "/courses", """
                {"name":"X","category":"MANDATORY","status":"MATR"}""")).hasStatus(404);
        String bobEnrollment = JsonPath.read(post(bob, "/api/school/enrollments", ENROLLMENT), "$.id");
        assertThat(send("POST", bob, "/api/school/enrollments/" + bobEnrollment + "/courses", """
                {"name":"X","category":"MANDATORY","status":"MATR","commitmentId":"%s"}""".formatted(commitment))).hasStatus(400);
        assertThat(mvc.get().uri("/api/school")).hasStatus(401);

        // Busca, arquivo da conta e saída do grupo.
        assertThat(JsonPath.<List<String>>read(body(send("GET", alice, "/api/search?q=gerencia&kinds=SCHOOL_COURSE", null)),
                "$.groups[0].items[*].link")).containsExactly("/escola/materias/" + course);
        String export = body(send("GET", alice, "/api/account/export", null));
        assertThat(JsonPath.<List<String>>read(export, "$.school[0].courses[*].code")).contains("DIM0517", "IMD0030");
        assertThat(send("POST", alice, "/api/groups/" + groupId + "/leave", null)).hasStatus2xxSuccessful();
        assertThat(JsonPath.<List<Object>>read(body(send("GET", bob, "/api/school", null)), "$.shared")).isEmpty();

        assertThat(send("DELETE", alice, "/api/school/enrollments/" + enrollment, null)).hasStatus(204);
        assertThat(send("GET", alice, "/api/school/courses/" + course, null)).hasStatus(404);
    }

    @Test
    void readsTheHistoryWithAi() throws Exception {
        String alice = registerAndGetBearer();
        when(gemini.enabled()).thenReturn(true);
        when(gemini.generateJson(anyString(), anyList())).thenReturn("""
                {"curso":"Engenharia","instituicao":"UFX","cargaExigida":{"obrigatorias":"2.870 h","optativas":540,"complementares":null},
                 "materias":[{"periodo":"2024/1","codigo":"MAT1","nome":"Cálculo I","ch":90,"tipo":"OBRIGATORIA","situacao":"APR",
                   "frequencia":"98,5","media":"8,7","conceito":null},
                   {"periodo":"x","codigo":null,"nome":"Inglês","ch":30,"tipo":"OPTATIVA","situacao":"QUALQUER","media":15},
                   {"nome":null}]}""");
        String read = body(upload(alice, "%PDF-1.7 histórico".getBytes(StandardCharsets.UTF_8)));
        assertThat(JsonPath.<String>read(read, "$.course")).isEqualTo("Engenharia");
        assertThat(JsonPath.<Integer>read(read, "$.required.mandatory")).isEqualTo(2870);
        assertThat(JsonPath.<List<String>>read(read, "$.courses[*].period")).containsExactly("2024.1", null);
        assertThat(JsonPath.<Double>read(read, "$.courses[0].average")).isEqualTo(8.7);
        assertThat(JsonPath.<String>read(read, "$.courses[1].status")).isEqualTo("MATR");
        assertThat(JsonPath.<Object>read(read, "$.courses[1].average")).isNull();
        assertThat(upload(alice, "texto qualquer".getBytes(StandardCharsets.UTF_8))).hasStatus(400);

        when(gemini.generateJson(anyString(), anyList())).thenReturn("{\"materias\":[]}");
        assertThat(upload(alice, "%PDF-1.7".getBytes(StandardCharsets.UTF_8))).hasStatus(502);
        when(gemini.enabled()).thenReturn(false);
        assertThat(upload(alice, "%PDF-1.7".getBytes(StandardCharsets.UTF_8))).hasStatus(503);
    }

    private MvcTestResult upload(String bearer, byte[] bytes) {
        return mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.POST, "/api/school/import/ai")
                .file(new MockMultipartFile("files", "historico.pdf", "application/pdf", bytes))
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private List<String> types(String bearer) {
        return JsonPath.read(body(send("GET", bearer, "/api/notifications?page=0&size=50", null)), "$.items[*].type");
    }
}
