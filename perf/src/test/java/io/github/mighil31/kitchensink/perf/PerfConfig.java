package io.github.mighil31.kitchensink.perf;

import static io.gatling.javaapi.http.HttpDsl.http;

import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Run settings, from system properties (-Dname=value):
 * <ul>
 *   <li>{@code baseUrl} – REST root (default: legacy)</li>
 *   <li>{@code user} – optional HTTP Basic user; password from env {@code PERF_PASSWORD}</li>
 *   <li>{@code rate} – arrivals per second (default 50)</li>
 *   <li>{@code duration} – seconds of steady load (default 180)</li>
 * </ul>
 */
public final class PerfConfig {

    private PerfConfig() {}

    public static String baseUrl() {
        String url = System.getProperty("baseUrl", "http://localhost:8080/kitchensink/rest");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public static double rate() {
        return Double.parseDouble(System.getProperty("rate", "50"));
    }

    public static Duration duration() {
        return Duration.ofSeconds(Long.parseLong(System.getProperty("duration", "180")));
    }

    /** {@code Authorization} header value when a user is configured. */
    public static Optional<String> basicAuthHeader() {
        String user = System.getProperty("user", "");
        if (user.isBlank()) {
            return Optional.empty();
        }
        String password = System.getenv("PERF_PASSWORD");
        if (password == null || password.isEmpty()) {
            throw new IllegalStateException("-Duser is set but env PERF_PASSWORD is not");
        }
        String token = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return Optional.of("Basic " + token);
    }

    public static HttpProtocolBuilder httpProtocol() {
        HttpProtocolBuilder protocol = http.baseUrl(baseUrl())
                .acceptHeader("application/json")
                .contentTypeHeader("application/json")
                .acceptLanguageHeader("en")
                .shareConnections();
        return basicAuthHeader().map(h -> protocol.authorizationHeader(h)).orElse(protocol);
    }
}
