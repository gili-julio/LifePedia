package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import com.lifepedia.api.financing.BcbClient;
import com.lifepedia.api.financing.CorrectionIndex;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class SimulatorIntegrationTest extends ApiTestSupport {

    private static final String RUN = "/api/finance/simulator/run";
    private static final String SAVED = "/api/finance/simulator/saved";

    private final YearMonth now = YearMonth.now();

    @MockitoBean
    BcbClient bcb;

    @Test
    void projectsTheBalanceWithChangesAndInvestment() {
        when(bcb.index(any(CorrectionIndex.class), anyInt())).thenReturn(List.of());
        String bearer = registerAndGetBearer();
        post(bearer, "/api/finance/recurring", """
                {"description":"Salário","type":"INCOME","categoryId":"%s","amount":5000,"startMonth":"%s"}
                """.formatted(categoryId(bearer, "INCOME", "Salário"), now));
        post(bearer, "/api/finance/recurring", """
                {"description":"Carro (1/2)","type":"EXPENSE","categoryId":"%s","amount":3000,"startMonth":"%s","endMonth":"%s"}
                """.formatted(categoryId(bearer, "EXPENSE", "Transporte"), now, now.plusMonths(1)));

        String input = """
                {"months":4,"startBalance":1000,"useFixed":true,"averageMonths":0,
                 "changes":[{"name":"Bônus","type":"INCOME","amount":500,"start":"%s","once":true},
                            {"name":"Curso","type":"EXPENSE","amount":200,"start":"%s","once":false}],
                 "investment":{"initial":0,"monthly":100,"source":"FIXED","rate":12,"tax":false,"fromBalance":true}}
                """.formatted(now.plusMonths(1), now.plusMonths(2));
        double r = Math.pow(1.12, 1d / 12);
        double gross = 100 * (r + r * r + r * r * r + r * r * r * r);
        assertThat(send("POST", bearer, RUN, input)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.base.fixedIncome").asNumber().isEqualTo(5000.0);
            assertThat(json).extractingPath("$.base.monthly").asNumber().isEqualTo(2000.0);
            assertThat(json).extractingPath("$.points.length()").isEqualTo(4);
            assertThat(json).extractingPath("$.summary.finalCurrent").asNumber().isEqualTo(15000.0);
            assertThat(json).extractingPath("$.points[3].cash").asNumber().isEqualTo(14700.0);
            assertThat(json).extractingPath("$.summary.contributed").asNumber().isEqualTo(400.0);
            assertThat(json).extractingPath("$.summary.finalSimulated").asNumber().satisfies(v ->
                    assertThat(v.doubleValue()).isCloseTo(14700 + gross, within(0.02)));
            assertThat(json).extractingPath("$.summary.yearlyRate").asNumber().isEqualTo(12.0);
        });
        assertThat(send("POST", bearer, RUN + "?real=true", input)).bodyJson()
                .extractingPath("$.summary.finalCurrent").asNumber().satisfies(v -> assertThat(v.doubleValue()).isLessThan(15000));
    }

    @Test
    void validatesTheInput() {
        String bearer = registerAndGetBearer();
        assertThat(send("POST", bearer, RUN, """
                {"months":0,"startBalance":0,"useFixed":true,"averageMonths":0}""")).hasStatus(400);
        assertThat(send("POST", bearer, RUN, """
                {"months":12,"startBalance":0,"useFixed":true,"averageMonths":5}""")).hasStatus(400);
        assertThat(send("POST", bearer, RUN, """
                {"months":12,"startBalance":0,"useFixed":true,"averageMonths":3,
                 "changes":[{"name":"X","type":"EXPENSE","amount":10,"start":"%s","end":"%s","once":false}]}
                """.formatted(now.plusMonths(2), now))).hasStatus(400);
        assertThat(send("POST", bearer, RUN, """
                {"months":12,"startBalance":0,"useFixed":true,"averageMonths":3,
                 "investment":{"initial":0,"monthly":100,"source":"CDI","tax":true,"fromBalance":true}}""")).hasStatus(400);
        assertThat(mvc.post().uri(RUN).header(CSRF, "test").contentType(MediaType.APPLICATION_JSON).content("{}")).hasStatus(401);
    }

    @Test
    void savesComparesAndIsolates() {
        when(bcb.index(any(CorrectionIndex.class), anyInt())).thenReturn(List.of());
        String alice = registerAndGetBearer();
        String bob = registerAndGetBearer();
        String a = JsonPath.read(post(alice, SAVED, """
                {"name":"Só saldo","input":{"months":12,"startBalance":100,"useFixed":true,"averageMonths":3}}"""), "$.id");
        String b = JsonPath.read(post(alice, SAVED, """
                {"name":"Com CDB","input":{"months":24,"startBalance":100,"useFixed":true,"averageMonths":3,
                 "investment":{"initial":1000,"monthly":0,"source":"CDI","rate":100,"tax":true,"fromBalance":true}}}"""), "$.id");

        assertThat(send("GET", alice, SAVED, null)).bodyJson().satisfies(json -> {
            assertThat(json).extractingPath("$.length()").isEqualTo(2);
            assertThat(json).extractingPath("$[0].input.investment.source").isEqualTo("CDI");
        });
        assertThat(send("PUT", alice, SAVED + "/" + a, """
                {"name":"Saldo puro","input":{"months":6,"startBalance":100,"useFixed":false,"averageMonths":0}}"""))
                .bodyJson().extractingPath("$.name").isEqualTo("Saldo puro");
        assertThat(send("POST", alice, "/api/finance/simulator/compare", """
                {"ids":["%s","%s"]}""".formatted(a, b))).bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$[0].points.length()").isEqualTo(6);
                    assertThat(json).extractingPath("$[1].name").isEqualTo("Com CDB");
                    assertThat(json).extractingPath("$[1].points.length()").isEqualTo(24);
                });

        assertThat(send("GET", bob, SAVED, null)).bodyJson().extractingPath("$.length()").isEqualTo(0);
        assertThat(send("POST", bob, "/api/finance/simulator/compare", """
                {"ids":["%s","%s"]}""".formatted(a, b))).hasStatus(404);
        assertThat(send("DELETE", bob, SAVED + "/" + a, null)).hasStatus(404);
        assertThat(send("GET", alice, "/api/account/export", null)).bodyJson()
                .extractingPath("$.financeSimulations.length()").isEqualTo(2);
        assertThat(send("DELETE", alice, SAVED + "/" + a, null)).hasStatus(204);
        assertThat(send("GET", alice, SAVED, null)).bodyJson().extractingPath("$.length()").isEqualTo(1);
    }
}
