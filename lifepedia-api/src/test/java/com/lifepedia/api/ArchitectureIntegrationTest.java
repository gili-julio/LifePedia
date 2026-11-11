package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ArchitectureIntegrationTest extends ApiTestSupport {

    private static final String GLB = "model/gltf-binary";

    @Value("${lifepedia.storage.dir}")
    private Path storage;

    @Test
    void projectVersionsAndModelFiles() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String projectId = project(alice, "Casa");

        assertThat(send("POST", alice, "/api/architecture/projects", """
                {"name":" "}""")).hasStatus(400);
        assertThat(send("POST", alice, "/api/architecture/projects", """
                {"name":"X","latitude":95}""")).hasStatus(400);

        byte[] model = glb(1000);
        String versionId = upload(alice, projectId, model);
        assertThat(send("GET", alice, "/api/architecture/projects/" + projectId, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.role").isEqualTo("OWNER");
            assertThat(json).extractingPath("$.currentVersionId").isEqualTo(versionId);
            assertThat(json).extractingPath("$.versions[0].number").isEqualTo(1);
            assertThat(json).extractingPath("$.usage.usedBytes").isEqualTo(model.length);
        });

        MvcTestResult file = send("GET", alice, "/api/architecture/versions/" + versionId + "/model", null);
        assertThat(file).hasStatusOk();
        assertThat(file.getResponse().getContentAsByteArray()).isEqualTo(model);
        Path onDisk = modelPath(file);
        assertThat(onDisk).exists();

        // Mesma reserva duas vezes, arquivo que não é GLB e arquivo maior que o informado.
        assertThat(putModel(alice, versionId, model)).hasStatus(409);
        String broken = start(alice, projectId, 100);
        assertThat(putModel(alice, broken, new byte[100])).hasStatus(400);
        String small = start(alice, projectId, 100);
        assertThat(putModel(alice, small, glb(1000))).hasStatus(400);

        // Outra pessoa não vê nem baixa; sem login, 401.
        assertThat(send("GET", bob, "/api/architecture/projects/" + projectId, null)).hasStatus(404);
        assertThat(send("GET", bob, "/api/architecture/versions/" + versionId + "/model", null)).hasStatus(404);
        assertThat(putModel(bob, small, model)).hasStatus(404);
        assertThat(mvc.get().uri("/api/architecture/projects").exchange()).hasStatus(401);
        assertThat(JsonPath.<List<?>>read(body(send("GET", bob, "/api/architecture/projects", null)), "$.owned")).isEmpty();

        String second = upload(alice, projectId, glb(800));
        assertThat(send("PUT", alice, "/api/architecture/projects/" + projectId + "/current", """
                {"versionId":"%s"}""".formatted(versionId))).bodyJson().extractingPath("$.currentVersionId").isEqualTo(versionId);
        assertThat(send("DELETE", alice, "/api/architecture/versions/" + versionId, null)).hasStatus(204);
        assertThat(send("GET", alice, "/api/architecture/projects/" + projectId, null))
                .bodyJson().extractingPath("$.currentVersionId").isEqualTo(second);
        assertThat(onDisk).doesNotExist();

        Path secondFile = modelPath(send("GET", alice, "/api/architecture/versions/" + second + "/model", null));
        assertThat(send("DELETE", alice, "/api/architecture/projects/" + projectId, null)).hasStatus(204);
        assertThat(secondFile).doesNotExist();
    }

    @Test
    void sizeLimitsAndQuota() {
        String alice = registerAndGetBearer();
        String projectId = project(alice, "Casa");
        String uri = "/api/architecture/projects/" + projectId + "/versions";
        assertThat(send("POST", alice, uri, """
                {"sizeBytes":700000}""")).hasStatus(400);
        assertThat(send("POST", alice, uri, """
                {"sizeBytes":0}""")).hasStatus(400);
        start(alice, projectId, 500_000);
        assertThat(send("POST", alice, uri, """
                {"sizeBytes":600000}""")).hasStatus(400);
        assertThat(JsonPath.<Integer>read(body(send("GET", alice, "/api/architecture/projects", null)), "$.usage.usedBytes"))
                .isEqualTo(500_000);
    }

    @Test
    void sharingRolesCommentsAndPublicLink() {
        String alice = registerAndGetBearer("Alice", newUsername());
        String bob = registerAndGetBearer("Bob", newUsername());
        String carol = registerAndGetBearer("Carol", newUsername());
        String bobId = userId(bob);
        String carolId = userId(carol);
        befriend(alice, bob);
        String projectId = project(alice, "Casa da praia");
        String versionId = upload(alice, projectId, glb(1000));
        String base = "/api/architecture/projects/" + projectId;

        assertThat(send("PUT", alice, base + "/shares/users/" + carolId, """
                {"role":"VIEW"}""")).hasStatus(400);
        assertThat(send("PUT", alice, base + "/shares/users/" + bobId, """
                {"role":"OWNER"}""")).hasStatus(400);
        assertThat(send("GET", alice, base + "/share-options", null)).bodyJson()
                .extractingPath("$.friends[0].id").isEqualTo(bobId);
        assertThat(send("PUT", alice, base + "/shares/users/" + bobId, """
                {"role":"VIEW"}""")).hasStatus(204);
        assertThat(JsonPath.<List<String>>read(body(send("GET", bob, "/api/notifications?page=0&size=10", null)), "$.items[*].type"))
                .contains("ARCHITECTURE_SHARED");

        // Só ver: abre e baixa, mas não comenta, não sobe versão e não mexe no compartilhamento.
        assertThat(send("GET", bob, "/api/architecture/projects", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.shared[0].id").isEqualTo(projectId);
            assertThat(json).extractingPath("$.shared[0].role").isEqualTo("VIEW");
        });
        assertThat(send("GET", bob, "/api/architecture/versions/" + versionId + "/model", null)).hasStatusOk();
        String comment = """
                {"versionId":"%s","body":"Trocar o piso","point":[1.5,0,2]}""".formatted(versionId);
        assertThat(send("POST", bob, base + "/comments", comment)).hasStatus(403);
        assertThat(send("POST", bob, base + "/versions", """
                {"sizeBytes":100}""")).hasStatus(403);
        assertThat(send("GET", bob, base + "/shares", null)).hasStatus(403);

        assertThat(send("PUT", alice, base + "/shares/users/" + bobId, """
                {"role":"COMMENT"}""")).hasStatus(204);
        String commentId = JsonPath.read(post(bob, base + "/comments", comment), "$.id");
        assertThat(send("POST", bob, base + "/comments", """
                {"versionId":"%s","body":"x","point":[1,"NaN",2]}""".formatted(versionId))).hasStatus(400);
        assertThat(JsonPath.<List<String>>read(body(send("GET", alice, "/api/notifications?page=0&size=10", null)), "$.items[*].type"))
                .contains("ARCHITECTURE_COMMENT");
        assertThat(send("GET", alice, base + "/comments?versionId=" + versionId, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$[0].body").isEqualTo("Trocar o piso");
            assertThat(json).extractingPath("$[0].canDelete").isEqualTo(true);
        });
        assertThat(send("PUT", alice, base + "/comments/" + commentId + "/resolved", """
                {"resolved":true}""")).bodyJson().extractingPath("$.resolved").isEqualTo(true);
        assertThat(send("POST", bob, base + "/views", """
                {"name":"Sala","position":[0,1.6,5],"target":[0,1.6,0]}""")).hasStatus(403);
        post(alice, base + "/views", """
                {"name":"Sala","position":[0,1.6,5],"target":[0,1.6,0]}""");

        // Grupo: quem é membro vê, com o nome do grupo.
        String groupId = JsonPath.read(post(alice, "/api/groups", """
                {"name":"Família"}"""), "$.id");
        String token = JsonPath.read(body(send("POST", alice, "/api/groups/" + groupId + "/invite-link", null)), "$.token");
        assertThat(send("POST", carol, "/api/groups/join/" + token, null)).hasStatusOk();
        assertThat(send("GET", carol, base, null)).hasStatus(404);
        assertThat(send("PUT", carol, base + "/shares/groups/" + groupId, """
                {"role":"VIEW"}""")).hasStatus(404);
        assertThat(send("PUT", alice, base + "/shares/groups/" + groupId, """
                {"role":"VIEW"}""")).hasStatus(204);
        assertThat(send("GET", carol, "/api/architecture/projects", null))
                .bodyJson().extractingPath("$.shared[0].sharedVia").isEqualTo("Família");
        assertThat(send("GET", carol, base, null)).bodyJson().extractingPath("$.views[0].name").isEqualTo("Sala");

        // Link público: sem login, só a maquete atual; apagado → 404.
        String link = body(send("POST", alice, base + "/links", """
                {"days":7}"""));
        String publicToken = JsonPath.read(link, "$.token");
        assertThat(send("POST", alice, base + "/links", """
                {"days":400}""")).hasStatus(400);
        assertThat(mvc.get().uri("/api/public/architecture/" + publicToken).exchange()).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.name").isEqualTo("Casa da praia");
            assertThat(json).extractingPath("$.version.number").isEqualTo(1);
        });
        assertThat(mvc.get().uri("/api/public/architecture/" + publicToken + "/model").exchange()).hasStatusOk();
        assertThat(mvc.get().uri("/api/public/architecture/nao-existe").exchange()).hasStatus(404);
        assertThat(send("DELETE", alice, base + "/links/" + JsonPath.read(link, "$.id"), null)).hasStatus(204);
        assertThat(mvc.get().uri("/api/public/architecture/" + publicToken).exchange()).hasStatus(404);

        assertThat(send("DELETE", bob, base + "/shares/me", null)).hasStatus(204);
        assertThat(send("GET", bob, base, null)).hasStatus(404);
    }

    @Test
    void attachmentsAndEditorRole() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        befriend(alice, bob);
        String projectId = project(alice, "Apartamento");
        String base = "/api/architecture/projects/" + projectId;
        assertThat(send("PUT", alice, base + "/shares/users/" + userId(bob), """
                {"role":"EDIT"}""")).hasStatus(204);

        // Quem edita sobe versão (conta no espaço da dona), anexa e cria vista; não compartilha nem exclui o projeto.
        upload(bob, projectId, glb(1000));
        assertThat(send("GET", alice, "/api/architecture/projects", null)).bodyJson()
                .extractingPath("$.usage.usedBytes").isEqualTo(1000);
        MvcTestResult image = attach(bob, base, new MockMultipartFile("file", "render.png", "image/png", png(200)));
        assertThat(image).hasStatus(201);
        assertThat(image).bodyJson().extractingPath("$.kind").isEqualTo("IMAGE");
        byte[] pdf = "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        MvcTestResult plan = attach(bob, base, new MockMultipartFile("file", "../planta baixa.pdf", "application/pdf", pdf));
        assertThat(plan).bodyJson().extractingPath("$.name").isEqualTo("planta baixa.pdf");
        assertThat(attach(bob, base, new MockMultipartFile("file", "x.txt", "text/plain", "oi".getBytes(StandardCharsets.UTF_8))))
                .hasStatus(400);

        String fileUrl = JsonPath.read(body(plan), "$.fileUrl");
        MvcTestResult opened = send("GET", alice, fileUrl, null);
        assertThat(opened).hasStatusOk();
        assertThat(opened.getResponse().getHeader("Content-Security-Policy")).isEqualTo("sandbox");
        assertThat(opened.getResponse().getContentType()).isEqualTo("application/pdf");

        assertThat(send("GET", bob, base + "/shares", null)).hasStatus(403);
        assertThat(send("DELETE", bob, base, null)).hasStatus(403);
        String other = registerAndGetBearer();
        assertThat(send("GET", other, fileUrl, null)).hasStatus(404);
        assertThat(JsonPath.<List<?>>read(body(send("GET", alice, base + "/attachments", null)), "$")).hasSize(2);
    }

    @Test
    void sceneVariantsRoomsAndLights() {
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String other = registerAndGetBearer();
        befriend(alice, bob);
        String projectId = project(alice, "Casa nova");
        String versionId = upload(alice, projectId, glb(1000));
        String base = "/api/architecture/projects/" + projectId;
        assertThat(send("PUT", alice, base + "/shares/users/" + userId(bob), """
                {"role":"COMMENT"}""")).hasStatus(204);

        String variant = """
                {"versionId":"%s","name":"Piso claro","materials":{"3":{"color":"#AABBCC"},"7":{"texture":"madeira-clara","scale":0.8},
                "9":{}}}""".formatted(versionId);
        String variantId = JsonPath.read(post(alice, base + "/variants", variant), "$.id");
        assertThat(send("POST", alice, base + "/variants", """
                {"versionId":"%s","name":"X","materials":{"abc":{"color":"#ffffff"}}}""".formatted(versionId))).hasStatus(400);
        assertThat(send("POST", alice, base + "/variants", """
                {"versionId":"%s","name":"X","materials":{"1":{"color":"vermelho"}}}""".formatted(versionId))).hasStatus(400);
        assertThat(send("PUT", alice, base + "/variants/" + variantId, """
                {"name":"Piso escuro","materials":{"3":{"color":"#112233"}}}""")).bodyJson()
                .extractingPath("$.materials.3.color").isEqualTo("#112233");

        String roomId = JsonPath.read(post(alice, base + "/rooms", """
                {"versionId":"%s","name":"Sala","point":[1,0,2],"ceiling":2.8,"area":18.456,
                "outline":[[0,0],[4,0],[4,4.6],[0,4.6]]}""".formatted(versionId)), "$.id");
        assertThat(send("POST", alice, base + "/rooms", """
                {"versionId":"%s","name":"X","point":[1,0,2],"outline":[[0]]}""".formatted(versionId))).hasStatus(400);
        post(alice, base + "/lights", """
                {"versionId":"%s","point":[1,2.5,2],"color":"#FFD9A0","intensity":1.5}""".formatted(versionId));
        assertThat(send("POST", alice, base + "/lights", """
                {"versionId":"%s","point":[1,2.5,2],"color":"#ffffff","intensity":9}""".formatted(versionId))).hasStatus(400);

        // Quem comenta vê a cena, mas não cria nem apaga; quem não tem acesso, 404.
        assertThat(send("GET", bob, base + "/scene?versionId=" + versionId, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.variants[0].name").isEqualTo("Piso escuro");
            assertThat(json).extractingPath("$.rooms[0].area").isEqualTo(18.46);
            assertThat(json).extractingPath("$.rooms[0].outline[2][1]").isEqualTo(4.6);
            assertThat(json).extractingPath("$.lights[0].color").isEqualTo("#ffd9a0");
        });
        assertThat(send("POST", bob, base + "/rooms", """
                {"versionId":"%s","name":"Quarto","point":[1,0,2],"outline":[]}""".formatted(versionId))).hasStatus(403);
        assertThat(send("DELETE", bob, base + "/variants/" + variantId, null)).hasStatus(403);
        assertThat(send("GET", other, base + "/scene?versionId=" + versionId, null)).hasStatus(404);

        // O link público leva a cena da versão atual; o arquivo da conta, os nomes.
        String token = JsonPath.read(post(alice, base + "/links", """
                {"days":3}"""), "$.token");
        assertThat(mvc.get().uri("/api/public/architecture/" + token).exchange()).bodyJson()
                .extractingPath("$.scene.rooms[0].name").isEqualTo("Sala");
        assertThat(send("GET", alice, "/api/account/export", null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.architecture.projects[0].variants[0]").isEqualTo("Piso escuro");
            assertThat(json).extractingPath("$.architecture.projects[0].rooms[0]").isEqualTo("Sala");
        });

        assertThat(send("PUT", alice, base + "/rooms/" + roomId, """
                {"name":"Sala de estar"}""")).bodyJson().extractingPath("$.name").isEqualTo("Sala de estar");
        assertThat(send("DELETE", alice, base + "/rooms/" + roomId, null)).hasStatus(204);
        assertThat(send("DELETE", alice, "/api/architecture/versions/" + versionId, null)).hasStatus(204);
        assertThat(send("GET", alice, base + "/scene?versionId=" + versionId, null)).hasStatus(400);
    }

    private String project(String bearer, String name) {
        return JsonPath.read(post(bearer, "/api/architecture/projects", """
                {"name":"%s","latitude":-5.8,"northDegrees":30}""".formatted(name)), "$.id");
    }

    private String start(String bearer, String projectId, long size) {
        return JsonPath.read(post(bearer, "/api/architecture/projects/" + projectId + "/versions", """
                {"sizeBytes":%d,"sourceName":"casa.dae","sourceBytes":%d}""".formatted(size, size * 10)), "$.id");
    }

    private String upload(String bearer, String projectId, byte[] model) {
        String id = start(bearer, projectId, model.length);
        assertThat(putModel(bearer, id, model)).hasStatusOk();
        return id;
    }

    private MvcTestResult putModel(String bearer, String versionId, byte[] model) {
        return mvc.put().uri("/api/architecture/versions/" + versionId + "/model")
                .header(HttpHeaders.AUTHORIZATION, bearer).contentType(GLB).content(model).exchange();
    }

    private MvcTestResult attach(String bearer, String base, MockMultipartFile file) {
        return mvc.perform(MockMvcRequestBuilders.multipart(HttpMethod.POST, base + "/attachments").file(file)
                .header(HttpHeaders.AUTHORIZATION, bearer));
    }

    private void befriend(String a, String b) {
        send("POST", a, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(userId(b)));
        assertThat(send("POST", b, "/api/friends/requests", """
                {"userId":"%s"}""".formatted(userId(a)))).bodyJson().extractingPath("$.status").isEqualTo("ACCEPTED");
    }

    private Path modelPath(MvcTestResult file) {
        String etag = file.getResponse().getHeader(HttpHeaders.ETAG).replace("\"", "");
        return storage.resolve("architecture").resolve(etag);
    }

    /** GLB 2.0 mínimo de {@code size} bytes (cabeçalho + bloco JSON com espaços). */
    private static byte[] glb(int size) {
        byte[] json = "{\"asset\":{\"version\":\"2.0\"}}".getBytes(StandardCharsets.UTF_8);
        int chunk = size - 20;
        byte[] padded = Arrays.copyOf(json, chunk);
        Arrays.fill(padded, json.length, chunk, (byte) ' ');
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x46546C67).putInt(2).putInt(size).putInt(chunk).putInt(0x4E4F534A).put(padded).array();
    }
}
