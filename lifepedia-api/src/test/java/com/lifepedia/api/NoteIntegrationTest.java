package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.note.NoteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class NoteIntegrationTest extends ApiTestSupport {

    private final LocalDate today = LocalDate.now(ZoneId.of("America/Sao_Paulo"));

    @Autowired
    NoteService notes;

    @Test
    void organizesSearchesLinksAndIsolatesUsers() {
        String me = registerAndGetBearer();
        String folder = id(post(me, "/api/notes/folders", """
                {"name":"Faculdade"}"""));
        String tag = id(post(me, "/api/notes/tags", """
                {"name":"Prova","color":"#FF0000"}"""));
        assertThat(send("POST", me, "/api/notes/folders", """
                {"name":"faculdade"}""")).hasStatus(409);
        String task = id(post(me, "/api/tasks", """
                {"title":"Estudar","dueDate":"%s"}""".formatted(today)));
        String commitment = id(post(me, "/api/attendance", """
                {"name":"Cálculo","color":"#3366ff","weekdays":[1],"time":"08:00","durationMinutes":100,
                 "startDate":"%s","endDate":null,"maxAbsences":2,"taskId":null}""".formatted(today)));
        String options = body(send("GET", me, "/api/notes/link-options", null));
        assertThat(JsonPath.<List<String>>read(options, "$.tasks[*].title")).containsExactly("Estudar");
        assertThat(JsonPath.<List<String>>read(options, "$.commitments[*].name")).containsExactly("Cálculo");

        String created = post(me, "/api/notes", note("Aula 1", "# Derivadas\\n- [ ] lista\\nVer [[Resumo]]", folder,
                tag, task, commitment, null));
        String aula = id(created);
        assertThat(JsonPath.<String>read(created, "$.task.title")).isEqualTo("Estudar");
        assertThat(JsonPath.<String>read(created, "$.commitment.name")).isEqualTo("Cálculo");
        assertThat(JsonPath.<Integer>read(created, "$.version")).isEqualTo(1);
        String resumo = id(post(me, "/api/notes", note("Resumo", "Fórmulas", null, null, null, null, null)));

        // Citada em: "Resumo" aparece citado pela "Aula 1".
        String got = body(send("GET", me, "/api/notes/" + resumo, null));
        assertThat(JsonPath.<List<String>>read(got, "$.backlinks[*].title")).containsExactly("Aula 1");
        assertThat(JsonPath.<List<String>>read(body(send("GET", me, "/api/notes/all", null)), "$[*].title"))
                .containsExactly("Aula 1", "Resumo");

        // Busca, filtros e o começo do texto sem markdown.
        String list = body(send("GET", me, "/api/notes?q=DERIVADAS", null));
        assertThat(JsonPath.<List<String>>read(list, "$.items[*].title")).containsExactly("Aula 1");
        assertThat(JsonPath.<String>read(list, "$.items[0].excerpt")).isEqualTo("Derivadas lista Ver Resumo");
        assertThat(titles(me, "/api/notes?folderId=" + folder)).containsExactly("Aula 1");
        assertThat(titles(me, "/api/notes?tagId=" + tag)).containsExactly("Aula 1");
        assertThat(titles(me, "/api/notes?commitmentId=" + commitment)).containsExactly("Aula 1");
        assertThat(titles(me, "/api/notes?q=100%25")).isEmpty();
        assertThat(JsonPath.<List<Integer>>read(body(send("GET", me, "/api/notes/labels", null)), "$.folders[*].count"))
                .containsExactly(1);

        // Fixada vem primeiro mesmo sendo a mais antiga.
        assertThat(send("PUT", me, "/api/notes/" + aula + "/pin", "{\"pinned\":true}")).hasStatus(204);
        assertThat(titles(me, "/api/notes")).containsExactly("Aula 1", "Resumo");
        assertThat(titles(me, "/api/notes?sort=title,desc")).containsExactly("Aula 1", "Resumo");

        // Versão: quem tinha a antiga recebe 409; duas edições seguidas viram uma linha na atividade.
        assertThat(send("PUT", me, "/api/notes/" + aula, note("Aula 1", "v2", folder, tag, task, commitment, 1L)))
                .hasStatusOk();
        assertThat(send("PUT", me, "/api/notes/" + aula, note("Aula 1", "v3", folder, tag, task, commitment, 2L)))
                .hasStatusOk();
        assertThat(send("PUT", me, "/api/notes/" + aula, note("Aula 1", "velha", folder, tag, task, commitment, 1L)))
                .hasStatus(409);
        assertThat(send("PUT", me, "/api/notes/" + aula, note("Aula 1", "x", folder, tag, task, commitment, null)))
                .hasStatus(400);

        // Arquivar, lixeira, restaurar e apagar de vez.
        assertThat(send("PUT", me, "/api/notes/" + resumo + "/archive", "{\"archived\":true}")).hasStatus(204);
        assertThat(titles(me, "/api/notes")).containsExactly("Aula 1");
        assertThat(titles(me, "/api/notes?status=ARCHIVED")).containsExactly("Resumo");
        assertThat(send("DELETE", me, "/api/notes/" + resumo + "/permanent", null)).hasStatus(400);
        assertThat(send("DELETE", me, "/api/notes/" + resumo, null)).hasStatus(204);
        assertThat(titles(me, "/api/notes?status=TRASH")).containsExactly("Resumo");
        assertThat(send("PUT", me, "/api/notes/" + resumo, note("Resumo", "x", null, null, null, null, 1L))).hasStatus(400);
        assertThat(send("POST", me, "/api/notes/" + resumo + "/restore", null)).hasStatus(204);
        assertThat(titles(me, "/api/notes?status=ARCHIVED")).containsExactly("Resumo");
        String activity = body(send("GET", me, "/api/notes/" + resumo + "/activity", null));
        assertThat(JsonPath.<List<String>>read(activity, "$[*].action"))
                .containsExactly("RESTORED", "TRASHED", "ARCHIVED", "CREATED");
        assertThat(JsonPath.<List<String>>read(body(send("GET", me, "/api/notes/" + aula + "/activity", null)),
                "$[*].action")).containsExactly("EDITED", "CREATED");

        // Modelos ficam fora da lista de notas.
        post(me, "/api/notes", note("Modelo de aula", "## Tópicos", null, null, null, null, null)
                .replace("\"template\":null", "\"template\":true"));
        assertThat(titles(me, "/api/notes?status=TEMPLATE")).containsExactly("Modelo de aula");
        assertThat(titles(me, "/api/notes")).doesNotContain("Modelo de aula");

        // Arquivo da conta leva as notas (só registro).
        String exported = body(send("GET", me, "/api/account/export", null));
        assertThat(JsonPath.<List<String>>read(exported, "$.notes[*].title")).contains("Aula 1", "Resumo", "Modelo de aula");
        assertThat(JsonPath.<List<String>>read(exported, "$.notes[?(@.title == 'Aula 1')].folder")).containsExactly("Faculdade");

        // Validação e isolamento.
        assertThat(send("POST", me, "/api/notes", note(" ", "x", null, null, null, null, null))).hasStatus(400);
        String other = registerAndGetBearer();
        String otherFolder = id(post(other, "/api/notes/folders", """
                {"name":"Minha"}"""));
        assertThat(send("POST", me, "/api/notes", note("X", "x", otherFolder, null, null, null, null))).hasStatus(400);
        assertThat(send("POST", other, "/api/notes", note("X", "x", null, null, task, null, null))).hasStatus(400);
        assertThat(send("POST", other, "/api/notes", note("X", "x", null, null, null, commitment, null))).hasStatus(400);
        assertThat(send("GET", other, "/api/notes/" + aula, null)).hasStatus(404);
        assertThat(send("PUT", other, "/api/notes/" + aula, note("X", "x", null, null, null, null, 3L))).hasStatus(404);
        assertThat(send("DELETE", other, "/api/notes/" + aula, null)).hasStatus(404);
        assertThat(send("GET", other, "/api/notes/" + aula + "/activity", null)).hasStatus(404);
        assertThat(titles(other, "/api/notes")).isEmpty();
        assertThat(mvc.get().uri("/api/notes").exchange()).hasStatus(401);

        // Pasta excluída só desliga a nota.
        assertThat(send("DELETE", me, "/api/notes/folders/" + folder, null)).hasStatus(204);
        assertThat(JsonPath.<Object>read(body(send("GET", me, "/api/notes/" + aula, null)), "$.folderId")).isNull();
    }

    @Test
    void storesImagesOnlyForTheOwner() {
        String me = registerAndGetBearer();
        String note = id(post(me, "/api/notes", note("Fotos", "", null, null, null, null, null)));
        MvcTestResult upload = mvc.perform(MockMvcRequestBuilders.multipart("/api/notes/" + note + "/images")
                .file(new MockMultipartFile("file", "f.png", "image/png", png(200)))
                .header(HttpHeaders.AUTHORIZATION, me));
        assertThat(upload).hasStatus(201);
        String url = JsonPath.read(body(upload), "$.url");
        assertThat(url).startsWith("/api/notes/" + note + "/images/");
        assertThat(send("GET", me, url, null)).hasStatusOk().hasContentType("image/jpeg");

        String other = registerAndGetBearer();
        assertThat(send("GET", other, url, null)).hasStatus(404);
        assertThat(send("GET", me, "/api/notes/" + note + "/images/naoexiste", null)).hasStatus(404);
    }

    @Test
    void groupNotesAreSharedWithDeleteRulesAndActivity() {
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Casa"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", bob, "/api/groups/join/" + token, null)).hasStatus2xxSuccessful();
        String base = "/api/groups/" + groupId + "/notes";

        String groupTask = id(post(alice, "/api/groups/" + groupId + "/tasks", """
                {"title":"Mercado"}"""));
        String note = id(post(alice, base, note("Lista", "- [ ] arroz", null, null, groupTask, null, null)));
        assertThat(send("POST", alice, base, note("Modelo", "x", null, null, null, null, null)
                .replace("\"template\":null", "\"template\":true"))).hasStatus(400);
        assertThat(send("GET", alice, base + "?status=TEMPLATE", null)).hasStatus(400);

        // Bob edita (membro), mas só quem criou ou o admin manda para a lixeira.
        String edited = body(send("PUT", bob, base + "/" + note, note("Lista", "- [x] arroz", null, null, groupTask, null, 1L)));
        assertThat(JsonPath.<String>read(edited, "$.editedBy.name")).isEqualTo("Bob");
        assertThat(JsonPath.<Boolean>read(edited, "$.canDelete")).isFalse();
        assertThat(send("DELETE", bob, base + "/" + note, null)).hasStatus(403);
        String activity = body(send("GET", alice, base + "/" + note + "/activity", null));
        assertThat(JsonPath.<List<String>>read(activity, "$[*].user.name")).containsExactly("Bob", "Alice");

        // Nota pessoal não aparece no grupo, e quem não é membro não vê nada.
        assertThat(titles(alice, "/api/notes")).isEmpty();
        assertThat(titles(bob, base)).containsExactly("Lista");
        String stranger = registerAndGetBearer();
        assertThat(send("GET", stranger, base, null)).hasStatus(404);
        assertThat(send("GET", stranger, base + "/" + note, null)).hasStatus(404);

        assertThat(send("DELETE", alice, base + "/" + note, null)).hasStatus(204);
        // Depois de 30 dias na lixeira, some de vez.
        notes.purgeTrash(OffsetDateTime.now().plusDays(31));
        assertThat(send("GET", alice, base + "/" + note, null)).hasStatus(404);
    }

    private List<String> titles(String bearer, String uri) {
        MvcTestResult result = send("GET", bearer, uri, null);
        assertThat(result).hasStatusOk();
        return JsonPath.read(body(result), "$.items[*].title");
    }

    private static String note(String title, String content, String folder, String tag, String task,
                               String commitment, Long version) {
        return """
                {"title":"%s","content":"%s","folderId":%s,"tagIds":%s,"icon":null,"color":null,"taskId":%s,
                 "commitmentId":%s,"template":null,"version":%s}"""
                .formatted(title, content, quoted(folder), tag == null ? "[]" : "[\"" + tag + "\"]", quoted(task),
                        quoted(commitment), version);
    }

    private static String quoted(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    private static String id(String json) {
        return JsonPath.read(json, "$.id");
    }
}
