package com.lifepedia.api.integration.google;

import com.lifepedia.api.config.GoogleProperties;
import com.lifepedia.api.config.IntegrationProperties;
import com.lifepedia.api.integration.ApiCalls;
import com.lifepedia.api.integration.IntegrationException;
import com.lifepedia.api.integration.IntegrationException.Kind;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** OAuth do Google (código de autorização) e leitura do Google Agenda, só com permissão de leitura. */
@Component
public class GoogleCalendarClient {

    public static final String SCOPE = "openid email https://www.googleapis.com/auth/calendar.readonly";
    private static final String AUTH = "https://accounts.google.com/o/oauth2/v2/auth";
    private static final String TOKEN = "https://oauth2.googleapis.com/token";
    private static final String REVOKE = "https://oauth2.googleapis.com/revoke";
    private static final String CALENDAR = "https://www.googleapis.com/calendar/v3";
    private static final int MAX_CALENDARS = 10;

    /** @param expiresIn segundos de validade do token de acesso */
    public record Tokens(String accessToken, String refreshToken, long expiresIn, String subject, String email, boolean calendarGranted) {

        @Override
        public String toString() {
            return "Tokens[" + subject + "]";
        }
    }

    public record RemoteEvent(String id, String title, OffsetDateTime start, OffsetDateTime end, boolean allDay, String location,
                              String meetUrl, String htmlUrl) {
    }

    private final GoogleProperties google;
    private final IntegrationProperties properties;
    private final ApiCalls api;
    private final JsonMapper json;

    public GoogleCalendarClient(GoogleProperties google, IntegrationProperties properties, ApiCalls api, JsonMapper json) {
        this.google = google;
        this.properties = properties;
        this.api = api;
        this.json = json;
    }

    /** Precisa do Client ID do login, do segredo dele e da chave de cifra. */
    public boolean enabled() {
        return google.enabled() && properties.googleClientSecret() != null && !properties.googleClientSecret().isBlank()
                && properties.tokensEnabled();
    }

    /** Endereço de consentimento; {@code prompt=consent} garante o token de renovação e deixa escolher outra conta. */
    public URI authorizeUrl(String state, String redirectUri) {
        return URI.create(AUTH + "?response_type=code&access_type=offline&include_granted_scopes=true"
                + "&prompt=" + encode("consent select_account")
                + "&client_id=" + encode(google.clientId())
                + "&redirect_uri=" + encode(redirectUri)
                + "&scope=" + encode(SCOPE)
                + "&state=" + encode(state));
    }

    public Tokens exchange(String code, String redirectUri) throws IntegrationException {
        JsonNode body = api.call("POST", URI.create(TOKEN), Map.of(), null, "grant_type=authorization_code"
                + "&code=" + encode(code)
                + "&client_id=" + encode(google.clientId())
                + "&client_secret=" + encode(properties.googleClientSecret())
                + "&redirect_uri=" + encode(redirectUri)).body();
        String access = text(body, "access_token");
        String idToken = text(body, "id_token");
        if (access == null || idToken == null) throw IntegrationException.of(Kind.INVALID);
        JsonNode claims = claims(idToken);
        String subject = text(claims, "sub");
        if (subject == null || !google.clientId().equals(text(claims, "aud"))) throw IntegrationException.of(Kind.INVALID);
        String scope = text(body, "scope");
        return new Tokens(access, text(body, "refresh_token"), body.path("expires_in").asLong(3600), subject, text(claims, "email"),
                scope != null && scope.contains("calendar.readonly"));
    }

    /** Token de acesso novo; renovação recusada (acesso revogado ou expirado) vira {@link Kind#AUTH}. */
    public Tokens refresh(String refreshToken) throws IntegrationException {
        try {
            JsonNode body = api.call("POST", URI.create(TOKEN), Map.of(), null, "grant_type=refresh_token"
                    + "&refresh_token=" + encode(refreshToken)
                    + "&client_id=" + encode(google.clientId())
                    + "&client_secret=" + encode(properties.googleClientSecret())).body();
            String access = text(body, "access_token");
            if (access == null) throw IntegrationException.of(Kind.INVALID);
            return new Tokens(access, null, body.path("expires_in").asLong(3600), null, null, true);
        } catch (IntegrationException e) {
            if (e.kind() == Kind.INVALID || e.kind() == Kind.FORBIDDEN) {
                throw new IntegrationException(Kind.AUTH, "O Google encerrou o acesso à agenda. Conecte de novo.");
            }
            throw e;
        }
    }

    /** Eventos das agendas marcadas na conta, de {@code from} a {@code to} (recusados e cancelados ficam de fora). */
    public List<RemoteEvent> events(String accessToken, OffsetDateTime from, OffsetDateTime to, ZoneId zone) throws IntegrationException {
        Map<String, String> headers = Map.of("Authorization", "Bearer " + accessToken);
        JsonNode calendars = api.get(URI.create(CALENDAR + "/users/me/calendarList?minAccessRole=reader&maxResults=50"), headers).body();
        List<String> ids = new ArrayList<>();
        for (JsonNode calendar : calendars.path("items")) {
            boolean selected = calendar.path("selected").asBoolean(false) || calendar.path("primary").asBoolean(false);
            String id = text(calendar, "id");
            if (selected && id != null && ids.size() < MAX_CALENDARS) ids.add(id);
        }
        List<RemoteEvent> events = new ArrayList<>();
        for (String calendarId : ids) {
            JsonNode list = api.get(URI.create(CALENDAR + "/calendars/" + encode(calendarId) + "/events?singleEvents=true"
                    + "&orderBy=startTime&maxResults=500&timeMin=" + encode(from.withOffsetSameInstant(ZoneOffset.UTC).toString())
                    + "&timeMax=" + encode(to.withOffsetSameInstant(ZoneOffset.UTC).toString())), headers).body();
            for (JsonNode event : list.path("items")) {
                RemoteEvent parsed = event(calendarId, event, zone);
                if (parsed != null) events.add(parsed);
            }
        }
        return events;
    }

    /** Desfaz o acesso no Google (melhor esforço: falha não impede desligar a conta). */
    public void revoke(String token) {
        try {
            api.call("POST", URI.create(REVOKE), Map.of(), null, "token=" + encode(token));
        } catch (IntegrationException ignored) {
            // A conta sai do LifePedia de qualquer jeito.
        }
    }

    private static RemoteEvent event(String calendarId, JsonNode event, ZoneId zone) {
        if ("cancelled".equals(text(event, "status"))) return null;
        for (JsonNode attendee : event.path("attendees")) {
            if (attendee.path("self").asBoolean(false) && "declined".equals(text(attendee, "responseStatus"))) return null;
        }
        String id = text(event, "id");
        if (id == null) return null;
        boolean allDay = event.path("start").has("date");
        OffsetDateTime start = when(event.path("start"), zone);
        OffsetDateTime end = when(event.path("end"), zone);
        if (start == null || end == null) return null;
        String meet = text(event, "hangoutLink");
        for (JsonNode entry : event.path("conferenceData").path("entryPoints")) {
            if (meet == null && "video".equals(text(entry, "entryPointType"))) meet = text(entry, "uri");
        }
        String title = text(event, "summary");
        return new RemoteEvent(calendarId + ":" + id, cut(title == null || title.isBlank() ? "(Sem título)" : title, 300), start, end,
                allDay, cut(text(event, "location"), 300), https(meet), https(text(event, "htmlLink")));
    }

    private static OffsetDateTime when(JsonNode node, ZoneId zone) {
        try {
            String dateTime = text(node, "dateTime");
            if (dateTime != null) return OffsetDateTime.parse(dateTime);
            String date = text(node, "date");
            return date == null ? null : LocalDate.parse(date).atStartOfDay(zone).toOffsetDateTime();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Lê o payload do ID token (veio direto do endpoint de token do Google por TLS, como permite o OpenID Connect). */
    private JsonNode claims(String idToken) throws IntegrationException {
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) throw IntegrationException.of(Kind.INVALID);
        try {
            return json.readTree(Base64.getUrlDecoder().decode(parts[1]));
        } catch (RuntimeException e) {
            throw IntegrationException.of(Kind.INVALID);
        }
    }

    private static String https(String url) {
        return url != null && url.startsWith("https://") && url.length() <= 500 ? url : null;
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
