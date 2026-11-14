package io.github.mighil31.kitchensink.contract.support;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for every contract test. Use {@code given(api)} (or {@code given().spec(api)})
 * so each request picks up the base URL, JSON content type, English messages and auth if configured.
 *
 * <pre>
 * given(api).when().get("/members/{id}", 0).then().statusCode(200);
 * </pre>
 */
public abstract class ContractTest {

    protected static final ContractConfig CONFIG = ContractConfig.fromEnvironment();

    protected static RequestSpecification api;

    @BeforeAll
    static void configureRestAssured() {
        // Print request + response only when an assertion on them fails.
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();

        RequestSpecBuilder builder = new RequestSpecBuilder()
                .setBaseUri(CONFIG.baseUrl())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                // Validation messages are locale-dependent; pin them so assertions are stable.
                .addHeader("Accept-Language", "en");

        CONFIG.credentials().ifPresent(c ->
                builder.setAuth(RestAssured.preemptive().basic(c.user(), c.password())));

        api = builder.build();
    }
}
