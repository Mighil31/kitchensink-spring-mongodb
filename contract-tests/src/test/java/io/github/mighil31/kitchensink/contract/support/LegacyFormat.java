package io.github.mighil31.kitchensink.contract.support;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * JBoss EAP kitchensink: POST returns 200 with an empty body and no Location,
 * ids are sequential numbers.
 */
final class LegacyFormat implements ApiFormat {

    private final RequestSpecification api;

    LegacyFormat(RequestSpecification api) {
        this.api = api;
    }

    @Override
    public int createdStatus() {
        return 200;
    }

    /** Legacy doesn't return the id, so find the member by its (unique) email in the list. */
    @Override
    public String idOfCreated(Response createResponse, Map<String, Object> requestBody) {
        Object email = requestBody.get("email");
        List<Map<String, Object>> members = given(api)
                .when().get("/members")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");

        List<Map<String, Object>> matches = members.stream()
                .filter(m -> email.equals(m.get("email")))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException("Expected exactly one member with email " + email + ", found " + matches.size());
        }
        return String.valueOf(matches.getFirst().get("id"));
    }

    @Override
    public String unknownId() {
        return "999999999";
    }
}
