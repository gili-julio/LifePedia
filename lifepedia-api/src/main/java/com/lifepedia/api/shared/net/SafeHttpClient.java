package com.lifepedia.api.shared.net;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.routing.RoutingSupport;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpException;
import org.apache.hc.core5.http.HttpHeaders;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Cliente HTTP para URLs vindas do usuário (proteção contra SSRF): só http(s) nas portas padrão, só IPs públicos
 * (checados na própria conexão, sem brecha de DNS rebinding), sem proxy nem cookies, redirecionamentos revalidados,
 * tempo total e tamanho da resposta limitados.
 */
@Component
public class SafeHttpClient implements DisposableBean {

    public static final String USER_AGENT = "Mozilla/5.0 (compatible; LifePedia-LinkPreview/1.0)";
    private static final int MAX_REDIRECTS = 4;
    private static final int MAX_URL_LENGTH = 2048;
    private static final Duration DEADLINE = Duration.ofSeconds(12);

    /** @param charset do Content-Type (nulo se não informado) */
    public record Response(URI url, String contentType, Charset charset, byte[] body) {
    }

    /** Falha ao buscar (bloqueado, fora do ar, grande demais...). A mensagem é técnica, não vai para o usuário. */
    public static class FetchException extends Exception {
        public FetchException(String message) {
            super(message);
        }
    }

    private final CloseableHttpClient client;

    public SafeHttpClient() {
        var connections = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new PublicDnsResolver())
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(5))
                        .setSocketTimeout(Timeout.ofSeconds(8))
                        .build())
                .setMaxConnTotal(20)
                .setMaxConnPerRoute(4)
                .build();
        this.client = HttpClients.custom()
                .setConnectionManager(connections)
                .disableRedirectHandling()
                .disableCookieManagement()
                .disableAuthCaching()
                .disableAutomaticRetries()
                .setUserAgent(USER_AGENT)
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .setResponseTimeout(Timeout.ofSeconds(8))
                        .build())
                .build();
    }

    /** Normaliza um link digitado (sem esquema vira https). Lança {@link IllegalArgumentException} se não for aceito. */
    public static URI parseUserUrl(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty() || value.length() > MAX_URL_LENGTH) throw new IllegalArgumentException("tamanho");
        if (!value.contains("://")) value = "https://" + value;
        try {
            URI uri = new URI(value.replace(" ", "%20"));
            checkUri(uri);
            String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
            String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
            return new URI(uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getHost().toLowerCase(Locale.ROOT)
                    + port + path + query);
        } catch (URISyntaxException | FetchException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    /**
     * GET seguindo redirecionamentos seguros.
     *
     * @param contentType aceita o Content-Type da resposta (em minúsculas)
     */
    public Response get(URI url, String accept, int maxBytes, Predicate<String> contentType) throws FetchException {
        long deadline = System.nanoTime() + DEADLINE.toNanos();
        URI current = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            checkUri(current);
            HttpGet get = new HttpGet(current);
            get.setHeader(HttpHeaders.ACCEPT, accept);
            get.setHeader(HttpHeaders.ACCEPT_LANGUAGE, "pt-BR,pt;q=0.9,en;q=0.5");
            try (ClassicHttpResponse response = client.executeOpen(RoutingSupport.determineHost(get), get, null)) {
                int status = response.getCode();
                if (status >= 300 && status < 400 && status != 304) {
                    Header location = response.getFirstHeader(HttpHeaders.LOCATION);
                    if (location == null) throw new FetchException("Redirecionamento sem destino.");
                    current = current.resolve(location.getValue().trim().replace(" ", "%20"));
                    continue;
                }
                if (status != 200) throw new FetchException("HTTP " + status);
                return read(current, response.getEntity(), maxBytes, contentType, deadline);
            } catch (IOException | HttpException | IllegalArgumentException e) {
                throw new FetchException("Falha ao buscar: " + e.getClass().getSimpleName());
            }
        }
        throw new FetchException("Redirecionamentos demais.");
    }

    private static Response read(URI url, HttpEntity entity, int maxBytes, Predicate<String> contentType, long deadline)
            throws FetchException, IOException {
        if (entity == null) throw new FetchException("Resposta vazia.");
        ContentType type = ContentType.parseLenient(entity.getContentType());
        String mime = type == null ? "" : type.getMimeType().toLowerCase(Locale.ROOT);
        if (!contentType.test(mime)) throw new FetchException("Tipo não aceito: " + mime);
        if (entity.getContentLength() > maxBytes) throw new FetchException("Resposta grande demais.");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = entity.getContent()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (out.size() + read > maxBytes) throw new FetchException("Resposta grande demais.");
                if (System.nanoTime() > deadline) throw new FetchException("Tempo esgotado.");
                out.write(buffer, 0, read);
            }
        }
        return new Response(url, mime, type == null ? null : type.getCharset(), out.toByteArray());
    }

    private static void checkUri(URI uri) throws FetchException {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) throw new FetchException("Esquema não aceito.");
        if (uri.getHost() == null || uri.getHost().isBlank()) throw new FetchException("Sem host.");
        if (uri.getRawUserInfo() != null) throw new FetchException("Usuário na URL.");
        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) throw new FetchException("Porta não aceita.");
        if (uri.toString().length() > MAX_URL_LENGTH) throw new FetchException("URL longa demais.");
    }

    /** Só IPs públicos da internet (nada de rede local, loopback, metadados de nuvem etc.). */
    static boolean isPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return false;
        }
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) return isPublicV4(b);
        if (address instanceof Inet6Address) {
            int first = b[0] & 0xff;
            if ((first & 0xfe) == 0xfc) return false; // fc00::/7 (ULA)
            if (first == 0x20 && b[1] == 0x01 && b[2] == 0x0d && (b[3] & 0xff) == 0xb8) return false; // 2001:db8::/32
            if (first == 0x20 && b[1] == 0x01 && b[2] == 0 && b[3] == 0) return false; // Teredo
            if (first == 0x20 && b[1] == 0x02) return isPublicV4(new byte[]{b[2], b[3], b[4], b[5]}); // 6to4
            if (first == 0 && b[1] == 0x64 && (b[2] & 0xff) == 0xff && (b[3] & 0xff) == 0x9b) { // 64:ff9b::/96
                return isPublicV4(new byte[]{b[12], b[13], b[14], b[15]});
            }
            boolean zeroPrefix = true;
            for (int i = 0; i < 10; i++) zeroPrefix &= b[i] == 0;
            if (zeroPrefix) return false; // ::/80 (inclui compatíveis com IPv4)
            return true;
        }
        return false;
    }

    private static boolean isPublicV4(byte[] b) {
        int a = b[0] & 0xff;
        int c = b[1] & 0xff;
        if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
        if (a == 100 && (c & 0xc0) == 64) return false; // 100.64.0.0/10
        if (a == 169 && c == 254) return false;
        if (a == 172 && (c & 0xf0) == 16) return false;
        if (a == 192 && c == 168) return false;
        if (a == 192 && c == 0 && (b[2] & 0xff) == 0) return false; // 192.0.0.0/24
        if (a == 192 && c == 0 && (b[2] & 0xff) == 2) return false;
        if (a == 198 && (c & 0xfe) == 18) return false; // 198.18.0.0/15
        if (a == 198 && c == 51 && (b[2] & 0xff) == 100) return false;
        return !(a == 203 && c == 0 && (b[2] & 0xff) == 113);
    }

    /** Resolve e recusa o host se qualquer endereço não for público. */
    static final class PublicDnsResolver implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (!isPublic(address)) throw new UnknownHostException("Endereço não público.");
            }
            return addresses;
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    }

    @Override
    public void destroy() throws IOException {
        client.close();
    }
}
