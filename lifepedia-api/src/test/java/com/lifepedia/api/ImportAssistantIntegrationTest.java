package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.finance.importing.ImportDraftJob;
import com.lifepedia.api.finance.importing.ImportDraftService;
import com.lifepedia.api.shared.ai.GeminiClient;
import com.lifepedia.api.shared.ai.GeminiClient.AiException;
import com.lifepedia.api.shared.ai.GeminiClient.Attachment;
import com.lifepedia.api.shared.ai.GeminiClient.Failure;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ImportAssistantIntegrationTest extends ApiTestSupport {

    private static final String RESULT = """
            {"format":"lifepedia/transactions@4","items":[{"descriptionNormal":"Padaria"},{"descriptionNormal":"Feira"}]}""";
    private static final String ROWS = """
            {"rows":"[{\\"key\\":\\"a\\"}]","itemCount":1}""";

    @MockitoBean
    GeminiClient gemini;

    @Autowired
    ImportDraftService drafts;

    @Autowired
    ImportDraftJob job;

    @BeforeEach
    void key() {
        when(gemini.enabled()).thenReturn(true);
    }

    @Test
    void readsInBackgroundAndKeepsTheReviewUntilImporting() throws Exception {
        String me = registerAndGetBearer();
        CountDownLatch release = new CountDownLatch(1);
        when(gemini.generateJson(anyString(), anyList())).thenAnswer(call -> {
            release.await();
            return RESULT;
        });

        MvcTestResult started = analyze(me, text("extrato.csv", "data;valor"));
        assertThat(started).hasStatus(202);
        String id = JsonPath.read(body(started), "$.id");
        assertThat((String) JsonPath.read(body(started), "$.status")).isEqualTo("RUNNING");
        assertThat(body(send("GET", me, "/api/finance/import/draft", null))).contains("\"status\":\"RUNNING\"", "\"fileCount\":1");
        assertThat(send("DELETE", me, "/api/finance/import/draft/" + id, null)).hasStatus(409);

        release.countDown();
        waitForStatus(me, "REVIEW");
        assertThat(body(send("GET", me, "/api/finance/import/draft", null))).contains("\"itemCount\":2");
        assertThat(body(send("GET", me, "/api/notifications?page=0&size=5", null))).contains("IMPORT_READY");

        String content = body(send("GET", me, "/api/finance/import/draft/" + id + "/content", null));
        assertThat((String) JsonPath.read(content, "$.result")).isEqualTo(RESULT);
        assertThat((String) JsonPath.read(content, "$.rows")).isNull();

        assertThat(send("PUT", me, "/api/finance/import/draft/" + id, ROWS)).hasStatusOk();
        content = body(send("GET", me, "/api/finance/import/draft/" + id + "/content", null));
        assertThat((String) JsonPath.read(content, "$.rows")).isEqualTo("[{\"key\":\"a\"}]");
        assertThat((String) JsonPath.read(content, "$.result")).isNull();

        String fixed = id(post(me, "/api/finance/recurring", """
                {"description":"Luz","type":"EXPENSE","categoryId":"%s","amount":100,"dayOfMonth":5,"startMonth":"2026-01"}
                """.formatted(categoryId(me, "EXPENSE", "Moradia"))));
        String request = """
                {"fixedAdjustments":[{"recurringEntryId":"%s","month":"2026-09","amount":120}],"draftId":"%s"}""".formatted(fixed, id);
        assertThat(send("POST", me, "/api/finance/import", request)).hasStatus(201);
        assertThat(send("GET", me, "/api/finance/import/draft", null)).hasStatus(204);
        // Revisão importada não importa de novo, e o conteúdo some.
        assertThat(send("POST", me, "/api/finance/import", request)).hasStatus(409);
        content = body(send("GET", me, "/api/finance/import/draft/" + id + "/content", null));
        assertThat((String) JsonPath.read(content, "$.result")).isNull();
    }

    @Test
    void limitsSendsPerAccountAndShowsThem() throws Exception {
        String me = registerAndGetBearer();
        when(gemini.generateJson(anyString(), anyList())).thenReturn(RESULT);

        String before = body(send("GET", me, "/api/finance/import/assistant", null));
        assertThat(before).contains("\"enabled\":true", "\"maxFiles\":10", "\"maxTotalBytes\":15728640", "\"maxPages\":50",
                "\"intervalHours\":4", "\"weekLimit\":5", "\"monthLimit\":10", "\"usedWeek\":0", "\"nextAt\":null");

        assertThat(analyze(me, text("a.txt", "a"))).hasStatus(202);
        String id = waitForStatus(me, "REVIEW");
        // Revisão aberta bloqueia outro envio; descartar não devolve o envio.
        assertThat(analyze(me, text("a.txt", "a"))).hasStatus(409);
        assertThat(send("DELETE", me, "/api/finance/import/draft/" + id, null)).hasStatus(204);

        MvcTestResult second = analyze(me, text("a.txt", "a"));
        assertThat(second).hasStatus(429);
        assertThat(body(second)).contains("O próximo libera em");
        String after = body(send("GET", me, "/api/finance/import/assistant", null));
        assertThat(after).contains("\"usedWeek\":1", "\"usedMonth\":1");
        assertThat((String) JsonPath.read(after, "$.quota.nextAt")).isNotNull();
    }

    @Test
    void failedReadDoesNotCountAndShowsTheReason() throws Exception {
        String me = registerAndGetBearer();
        when(gemini.generateJson(anyString(), anyList())).thenThrow(new AiException(Failure.BUSY));

        assertThat(analyze(me, text("a.txt", "a"))).hasStatus(202);
        String id = waitForStatus(me, "FAILED");
        assertThat(body(send("GET", me, "/api/finance/import/draft", null))).contains("sobrecarregada");
        assertThat(body(send("GET", me, "/api/notifications?page=0&size=5", null))).contains("IMPORT_FAILED");
        assertThat(body(send("GET", me, "/api/finance/import/assistant", null))).contains("\"usedWeek\":0", "\"nextAt\":null");

        assertThat(send("DELETE", me, "/api/finance/import/draft/" + id, null)).hasStatus(204);
        assertThat(send("GET", me, "/api/finance/import/draft", null)).hasStatus(204);
    }

    @Test
    void answerWithoutItemsFails() throws Exception {
        String me = registerAndGetBearer();
        when(gemini.generateJson(anyString(), anyList())).thenReturn("{\"items\":[]}");
        assertThat(analyze(me, text("a.txt", "a"))).hasStatus(202);
        waitForStatus(me, "FAILED");
        assertThat(body(send("GET", me, "/api/finance/import/draft", null))).contains("Não encontrei lançamentos");
    }

    @Test
    void checksDocumentsBeforeSending() throws Exception {
        String me = registerAndGetBearer();
        assertThat(analyze(me)).hasStatus(400);
        byte[] zip = {0x50, 0x4b, 0, 0};
        assertThat(analyze(me, new MockMultipartFile("files", "planilha.xlsx", "application/zip", zip))).hasStatus(400);
        assertThat(analyze(me, text("vazio.txt", ""))).hasStatus(400);

        MvcTestResult manyPages = analyze(me, pdf("grande.pdf", 51, null));
        assertThat(manyPages).hasStatus(400);
        assertThat(body(manyPages)).contains("grande.pdf tem 51 páginas");

        MvcTestResult locked = analyze(me, pdf("fatura.pdf", 1, "123456"));
        assertThat(locked).hasStatus(400);
        assertThat(body(locked)).contains("fatura.pdf tem senha");

        byte[] broken = "%PDF-1.7 quebrado".getBytes(StandardCharsets.UTF_8);
        assertThat(analyze(me, new MockMultipartFile("files", "a.pdf", "application/pdf", broken))).hasStatus(400);

        MockMultipartFile[] tooMany = new MockMultipartFile[11];
        Arrays.fill(tooMany, text("a.txt", "a"));
        assertThat(analyze(me, tooMany)).hasStatus(400);
        assertThat(analyze(me, new MockMultipartFile("files", "a.txt", "text/plain", new byte[15 * 1024 * 1024 + 1]))).hasStatus(400);

        verify(gemini, never()).generateJson(anyString(), any());
        assertThat(send("GET", me, "/api/finance/import/draft", null)).hasStatus(204);
    }

    @Test
    void detectsTheTypeFromTheContent() throws Exception {
        String me = registerAndGetBearer();
        when(gemini.generateJson(anyString(), anyList())).thenReturn(RESULT);
        MockMultipartFile latin = new MockMultipartFile("files", "extrato.csv", "text/csv", "AÇÃO;10".getBytes("windows-1252"));

        assertThat(analyze(me, pdf("disfarcado.txt", 50, null), latin,
                new MockMultipartFile("files", "foto.bin", "application/octet-stream", png(40)))).hasStatus(202);
        waitForStatus(me, "REVIEW");

        ArgumentCaptor<String> prompt = ArgumentCaptor.captor();
        ArgumentCaptor<List<Attachment>> documents = ArgumentCaptor.captor();
        verify(gemini).generateJson(prompt.capture(), documents.capture());
        assertThat(prompt.getValue()).contains("lifepedia/transactions@4", "- Moradia");
        assertThat(documents.getValue()).extracting(Attachment::mimeType)
                .containsExactly("application/pdf", "text/plain", "image/png");
        assertThat(new String(documents.getValue().get(1).data(), StandardCharsets.UTF_8)).isEqualTo("AÇÃO;10");
    }

    @Test
    void manualDraftKeepsTheReviewWithoutUsingTheLimits() {
        String me = registerAndGetBearer();
        String id = id(post(me, "/api/finance/import/draft", ROWS));
        assertThat(body(send("GET", me, "/api/finance/import/draft", null))).contains("\"origin\":\"MANUAL\"", "\"status\":\"REVIEW\"");
        assertThat(send("POST", me, "/api/finance/import/draft", ROWS)).hasStatus(409);
        assertThat(send("PUT", me, "/api/finance/import/draft/" + id, """
                {"rows":"[{\\"key\\":\\"b\\"}]","itemCount":1}""")).hasStatusOk();
        assertThat(body(send("GET", me, "/api/finance/import/draft/" + id + "/content", null))).contains("\\\"b\\\"");
        assertThat(send("PUT", me, "/api/finance/import/draft/" + id, "{\"rows\":\"\",\"itemCount\":0}")).hasStatus(400);
        assertThat(body(send("GET", me, "/api/finance/import/assistant", null))).contains("\"usedMonth\":0");
        assertThat(send("DELETE", me, "/api/finance/import/draft/" + id, null)).hasStatus(204);
        assertThat(send("GET", me, "/api/finance/import/draft/" + id + "/content", null)).hasStatus(404);
    }

    @Test
    void draftBelongsToItsOwner() {
        String me = registerAndGetBearer();
        String other = registerAndGetBearer();
        String id = id(post(me, "/api/finance/import/draft", ROWS));
        assertThat(send("GET", other, "/api/finance/import/draft", null)).hasStatus(204);
        assertThat(send("GET", other, "/api/finance/import/draft/" + id + "/content", null)).hasStatus(404);
        assertThat(send("PUT", other, "/api/finance/import/draft/" + id, ROWS)).hasStatus(404);
        assertThat(send("DELETE", other, "/api/finance/import/draft/" + id, null)).hasStatus(404);
        assertThat(send("POST", other, "/api/finance/import", """
                {"fixedAdjustments":[],"transactions":[{"date":"2026-09-01","description":"X","type":"EXPENSE","categoryId":"%s","amount":1}],"draftId":"%s"}
                """.formatted(categoryId(other, "EXPENSE", "Moradia"), id))).hasStatus(409);
    }

    @Test
    void lostReadStopsAndForgottenReviewIsCleaned() {
        String me = registerAndGetBearer();
        drafts.start(UUID.fromString(userId(me)), 1);
        drafts.failStale(OffsetDateTime.now().plusMinutes(1));
        assertThat(body(send("GET", me, "/api/finance/import/draft", null))).contains("\"status\":\"FAILED\"", "interrompida");

        job.failStale();
        drafts.purge(OffsetDateTime.now().plusDays(31));
        assertThat(send("GET", me, "/api/finance/import/draft", null)).hasStatus(204);
    }

    @Test
    void offWithoutKeyAndClosedToAnonymous() throws Exception {
        String me = registerAndGetBearer();
        when(gemini.enabled()).thenReturn(false);
        assertThat(body(send("GET", me, "/api/finance/import/assistant", null))).contains("\"enabled\":false");
        assertThat(analyze(me, text("a.txt", "a"))).hasStatus(404);
        assertThat(body(send("GET", me, "/api/finance/import/prompt", null))).contains("lifepedia/transactions@4");

        assertThat(mvc.get().uri("/api/finance/import/prompt").exchange()).hasStatus(401);
        assertThat(mvc.get().uri("/api/finance/import/draft").exchange()).hasStatus(401);
        assertThat(mvc.perform(MockMvcRequestBuilders.multipart("/api/finance/import/analyze")
                .file(text("a.txt", "a")).header(CSRF, "test"))).hasStatus(401);
    }

    private MvcTestResult analyze(String bearer, MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder request = MockMvcRequestBuilders.multipart("/api/finance/import/analyze");
        for (MockMultipartFile file : files) request.file(file);
        request.header(HttpHeaders.AUTHORIZATION, bearer);
        return mvc.perform(request);
    }

    /** Espera a leitura em segundo plano; devolve o id da revisão. */
    private String waitForStatus(String bearer, String status) throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            MvcTestResult result = send("GET", bearer, "/api/finance/import/draft", null);
            if (result.getResponse().getStatus() == 200 && status.equals(JsonPath.read(body(result), "$.status"))) {
                return JsonPath.read(body(result), "$.id");
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Leitura não chegou em " + status);
    }

    private static String id(String json) {
        return JsonPath.read(json, "$.id");
    }

    private static MockMultipartFile text(String name, String content) {
        return new MockMultipartFile("files", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile pdf(String name, int pages, String password) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 0; i < pages; i++) document.addPage(new PDPage());
            if (password != null) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("dono", password, new AccessPermission());
                policy.setEncryptionKeyLength(128);
                document.protect(policy);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return new MockMultipartFile("files", name, "application/pdf", out.toByteArray());
        }
    }
}
