package com.lifepedia.api.delivery;

import com.lifepedia.api.shared.net.SafeHttpClient;
import com.lifepedia.api.shared.net.SafeHttpClient.FetchException;
import com.lifepedia.api.shared.net.SafeHttpClient.Response;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Endereço pelo CEP no ViaCEP (só o CEP sai do servidor). Falha ou CEP inexistente = vazio. */
@Component
public class CepClient {

    private static final int MAX_BYTES = 16 * 1024;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    public record Address(String cep, String street, String district, String city, String state) {
    }

    private final SafeHttpClient http;

    public CepClient(SafeHttpClient http) {
        this.http = http;
    }

    /** @param cep 8 dígitos */
    public Optional<Address> find(String cep) {
        try {
            Response response = http.get(URI.create("https://viacep.com.br/ws/" + cep + "/json/"), "application/json",
                    MAX_BYTES, type -> type.contains("json"));
            JsonNode node = JSON.readTree(new String(response.body(),
                    response.charset() == null ? StandardCharsets.UTF_8 : response.charset()));
            if (node.path("erro").asBoolean(false) || node.path("localidade").asString("").isBlank()) return Optional.empty();
            return Optional.of(new Address(cep, node.path("logradouro").asString(""), node.path("bairro").asString(""),
                    node.path("localidade").asString(""), node.path("uf").asString("")));
        } catch (FetchException | JacksonException e) {
            return Optional.empty();
        }
    }
}
