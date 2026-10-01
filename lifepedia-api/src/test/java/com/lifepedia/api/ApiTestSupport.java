package com.lifepedia.api;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Base dos testes de integração. */
@ActiveProfiles("test")
abstract class ApiTestSupport {

    static final String CSRF = "X-Requested-With";

    @Autowired
    MockMvcTester mvc;

    String registerAndGetBearer() {
        return registerAndGetBearer("Teste", newUsername());
    }

    String registerAndGetBearer(String name, String username) {
        MvcTestResult result = mvc.post().uri("/api/auth/register")
                .header("X-Auth-Mode", "token").header(CSRF, "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","username":"%s","email":"%s@lifepedia.dev","password":"segredo123"}
                        """.formatted(name, username, UUID.randomUUID()))
                .exchange();
        assertThat(result).hasStatus(201);
        return "Bearer " + JsonPath.read(body(result), "$.token");
    }

    /** Username único para o teste. */
    static String newUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    String userId(String bearer) {
        return JsonPath.read(body(send("GET", bearer, "/api/users/me", null)), "$.id");
    }

    String categoryId(String bearer, String type, String name) {
        MvcTestResult result = mvc.get().uri("/api/finance/categories/all?type=" + type)
                .header(HttpHeaders.AUTHORIZATION, bearer).exchange();
        assertThat(result).hasStatusOk();
        List<String> ids = JsonPath.read(body(result), "$[?(@.name == '" + name + "')].id");
        assertThat(ids).as("categoria %s/%s", type, name).hasSize(1);
        return ids.getFirst();
    }

    MvcTestResult send(String method, String bearer, String uri, String json) {
        var request = switch (method) {
            case "POST" -> mvc.post().uri(uri);
            case "PUT" -> mvc.put().uri(uri);
            case "DELETE" -> mvc.delete().uri(uri);
            default -> mvc.get().uri(uri);
        };
        request.header(HttpHeaders.AUTHORIZATION, bearer);
        if (json != null) request.contentType(MediaType.APPLICATION_JSON).content(json);
        return request.exchange();
    }

    String post(String bearer, String uri, String json) {
        MvcTestResult result = send("POST", bearer, uri, json);
        assertThat(result).hasStatus(201);
        return body(result);
    }

    static String body(MvcTestResult result) {
        try {
            return result.getResponse().getContentAsString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
