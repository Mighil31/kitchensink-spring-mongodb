package io.github.mighil31.kitchensink;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

/** The whole app on a real server: MongoDB, probes, metrics, security and the registration flow. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMetrics // tests switch metrics export off by default; this one checks the Prometheus endpoint
class KitchensinkApplicationIT {

    @Autowired
    MongoTemplate mongoTemplate;

    @Value("${local.server.port}")
    int port;

    @Value("${local.management.port}")
    int managementPort;

    @Autowired
    HealthEndpointGroups healthGroups;

    @Test
    void connectsToMongo() {
        Document reply = mongoTemplate.executeCommand(new Document("ping", 1));

        assertThat(reply.get("ok")).isIn(1, 1.0);
    }

    /** Probes for the load balancer or Kubernetes, on the public port. */
    @Test
    void probesAreOnThePublicPort() throws Exception {
        for (String probe : List.of("/livez", "/readyz")) {
            HttpResponse<String> response =
                    HttpClient.newHttpClient().send(request(probe).build(), BODY);

            assertThat(response.statusCode()).as(probe).isEqualTo(200);
            assertThat(response.body()).as(probe).contains("\"status\":\"UP\"");
        }
    }

    /** Ready means MongoDB is reachable; alive doesn't depend on it (a restart wouldn't fix it). */
    @Test
    void readinessIncludesMongoAndLivenessDoesNot() {
        assertThat(healthGroups.get("readiness").isMember("mongo")).isTrue();
        assertThat(healthGroups.get("liveness").isMember("mongo")).isFalse();
    }

    /** Metrics are scraped from the internal management port; the public port doesn't serve them. */
    @Test
    void metricsOnlyOnTheManagementPort() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        client.send(request("/readyz").build(), BODY); // at least one request to count

        HttpResponse<String> internal = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + managementPort + "/actuator/prometheus"))
                        .build(),
                BODY);
        assertThat(internal.statusCode()).isEqualTo(200);
        assertThat(internal.body()).contains("http_server_requests_seconds_count");

        for (String path : List.of("/actuator/prometheus", "/actuator/health")) {
            assertThat(client.send(request(path).build(), BODY).statusCode())
                    .as("public " + path)
                    .isEqualTo(404);
        }
    }

    /** Every response carries the request id, including errors from the security filters. */
    @Test
    void responsesCarryARequestId() throws Exception {
        HttpResponse<String> unauthorized =
                HttpClient.newHttpClient().send(request("/rest/members").build(), BODY);

        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(unauthorized.headers().firstValue("X-Request-Id"))
                .hasValueSatisfying(id -> assertThat(id).matches("[0-9a-f]{32}"));
    }

    /**
     * Post/Redirect/Get through the real server, CSRF token included: the session (created when the
     * form renders its token) lives in a cookie only, never in the page's URLs or the redirect.
     */
    @Test
    void registrationRedirectKeepsSessionIdOutOfTheUrl() throws Exception {
        HttpClient browser =
                HttpClient.newBuilder().cookieHandler(new CookieManager()).build();

        HttpResponse<String> page = browser.send(request("/").build(), HttpResponse.BodyHandlers.ofString());
        assertThat(page.body())
                .doesNotContainIgnoringCase("jsessionid")
                .doesNotContainIgnoringCase("kitchensink_session");
        assertThat(page.headers().allValues("Set-Cookie"))
                .anySatisfy(cookie ->
                        assertThat(cookie).startsWith("KITCHENSINK_SESSION=").contains("HttpOnly", "SameSite=Lax"));
        Matcher token = CSRF_INPUT.matcher(page.body());
        assertThat(token.find()).as("CSRF token in the form").isTrue();

        HttpResponse<String> response = browser.send(
                request("/")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("name=Ann+Lee&email=prg-" + System.nanoTime()
                                + "%40example.com&phoneNumber=2125550000&_csrf="
                                + URLEncoder.encode(token.group(1), StandardCharsets.UTF_8)))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location)
                        .endsWith("/")
                        .doesNotContainIgnoringCase("jsessionid")
                        .doesNotContainIgnoringCase("kitchensink_session"));
    }

    /** The real bcrypt check, end to end (ADR 0007). */
    @Test
    void apiNeedsTheConfiguredClientCredentials() throws Exception {
        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> anonymous = client.send(request("/rest/members").build(), BODY);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.headers().firstValue("WWW-Authenticate")).hasValue("Basic realm=\"kitchensink\"");

        assertThat(client.send(basic("/rest/members", "test-client", "wrong"), BODY)
                        .statusCode())
                .isEqualTo(401);
        assertThat(client.send(basic("/rest/members", "test-client", "test-password"), BODY)
                        .statusCode())
                .isEqualTo(200);
    }

    private static final Pattern CSRF_INPUT = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");
    private static final HttpResponse.BodyHandler<String> BODY = HttpResponse.BodyHandlers.ofString();

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpRequest basic(String path, String user, String password) {
        String credentials =
                Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return request(path).header("Authorization", "Basic " + credentials).build();
    }
}
