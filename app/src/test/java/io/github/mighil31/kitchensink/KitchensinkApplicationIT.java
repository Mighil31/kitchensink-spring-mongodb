package io.github.mighil31.kitchensink;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    /** Post/Redirect/Get through the real server: the redirect must not carry the session id. */
    @Test
    void registrationRedirectKeepsSessionIdOutOfTheUrl() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(HttpRequest.BodyPublishers.ofString("name=Ann+Lee&email=prg-" + System.nanoTime()
                                        + "%40example.com" + "&phoneNumber=2125550000"))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location"))
                .hasValueSatisfying(
                        location -> assertThat(location).endsWith("/").doesNotContainIgnoringCase("jsessionid"));
        assertThat(response.headers().allValues("Set-Cookie"))
                .anySatisfy(
                        cookie -> assertThat(cookie).startsWith("JSESSIONID=").contains("HttpOnly"));
    }
}
