package com.vitalsafe.api.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestBase {

    protected static final String PASSWORD = "Str0ng-Test-Passw0rd";

    // One container shared by every test class (started once, removed when the JVM exits)
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private static final AtomicInteger IP_COUNTER = new AtomicInteger();

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        // Throwaway signing secret (32 bytes, base64) for tests only
        registry.add("app.jwt.secret",
                () -> Base64.getEncoder().encodeToString("j".repeat(32).getBytes(StandardCharsets.UTF_8)));
    }

    @Value("${local.server.port}")
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    protected record Session(String email, String accessToken, String refreshToken) {}

    // ---------- HTTP ----------

    /** Every call comes from its own fake client address, so tests never share a rate-limit bucket. */
    protected HttpResponse<String> call(String method, String path, String body, Map<String, String> headers)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(30));
        builder.setHeader("X-Forwarded-For", uniqueIp());
        if (body != null) {
            builder.setHeader("Content-Type", "application/json");
        }
        headers.forEach(builder::setHeader);
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    protected HttpResponse<String> get(String path) throws Exception {
        return call("GET", path, null, Map.of());
    }

    protected HttpResponse<String> getAs(String path, String bearerToken) throws Exception {
        return call("GET", path, null, Map.of("Authorization", "Bearer " + bearerToken));
    }

    protected HttpResponse<String> post(String path, String body) throws Exception {
        return call("POST", path, body, Map.of());
    }

    // ---------- test data ----------

    protected static String uniqueIp() {
        int n = IP_COUNTER.incrementAndGet();
        return "10.9." + (n / 250) + "." + (n % 250 + 1);
    }

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    protected HttpResponse<String> registerRaw(String email, String password, Object consent) throws Exception {
        return post("/api/v1/auth/register", json("email", email, "password", password, "consent", consent));
    }

    protected HttpResponse<String> loginRaw(String email, String password) throws Exception {
        return post("/api/v1/auth/login", json("email", email, "password", password));
    }

    protected Session registerUser() throws Exception {
        HttpResponse<String> response = registerRaw(uniqueEmail(), PASSWORD, true);
        assertThat(response.statusCode()).as("registering a test user").isEqualTo(201);
        return sessionFrom(response);
    }

    protected Session sessionFrom(HttpResponse<String> response) {
        return new Session(
                field(response.body(), "email"),
                field(response.body(), "token"),
                cookieValue(response, "refresh_token"));
    }

    // ---------- tiny JSON and cookie helpers (no dependency on a JSON library) ----------

    protected static String json(Object... pairs) {
        StringBuilder out = new StringBuilder("{");
        for (int i = 0; i < pairs.length; i += 2) {
            if (i > 0) {
                out.append(',');
            }
            out.append('"').append(pairs[i]).append("\":");
            Object value = pairs[i + 1];
            if (value instanceof Boolean) {
                out.append(value);
            } else {
                out.append('"').append(String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        return out.append('}').toString();
    }

    /** Reads a top-level string field from a JSON body, or null. */
    protected static String field(String body, String name) {
        Matcher matcher = Pattern
                .compile("\"" + Pattern.quote(name) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
                .matcher(body);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** The full Set-Cookie header for the named cookie, or null. */
    protected static String setCookieHeader(HttpResponse<?> response, String name) {
        return response.headers().allValues("Set-Cookie").stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .orElse(null);
    }

    protected static String cookieValue(HttpResponse<?> response, String name) {
        String header = setCookieHeader(response, name);
        return header == null ? null : header.substring(name.length() + 1).split(";", 2)[0];
    }
}