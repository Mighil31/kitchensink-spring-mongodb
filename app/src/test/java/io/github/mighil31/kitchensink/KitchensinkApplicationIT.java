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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Walking skeleton: the app starts, reaches MongoDB and reports healthy. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class KitchensinkApplicationIT {

    @Autowired
    MongoTemplate mongoTemplate;

    @Value("${local.server.port}")
    int port;

    @Test
    void connectsToMongo() {
        Document reply = mongoTemplate.executeCommand(new Document("ping", 1));

        assertThat(reply.get("ok")).isIn(1, 1.0);
    }

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"");
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
        assertThat(page.body()).doesNotContainIgnoringCase("jsessionid");
        assertThat(page.headers().allValues("Set-Cookie"))
                .anySatisfy(
                        cookie -> assertThat(cookie).startsWith("JSESSIONID=").contains("HttpOnly", "SameSite=Lax"));
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
                .hasValueSatisfying(
                        location -> assertThat(location).endsWith("/").doesNotContainIgnoringCase("jsessionid"));
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
