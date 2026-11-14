package io.github.mighil31.kitchensink.contract.support;

import io.restassured.response.Response;
import java.util.Map;

/**
 * Spring Boot + MongoDB app: POST returns 201 + Location, ids are 24-hex ObjectIds.
 * Not exercised until the new app exists (Phase 4).
 */
final class V2Format implements ApiFormat {

    @Override
    public int createdStatus() {
        return 201;
    }

    @Override
    public String idOfCreated(Response createResponse, Map<String, Object> requestBody) {
        String location = createResponse.header("Location");
        if (location == null) {
            throw new IllegalStateException("201 response has no Location header");
        }
        return location.substring(location.lastIndexOf('/') + 1);
    }

    @Override
    public String unknownId() {
        return "000000000000000000000000";
    }

    /** ProblemDetail with an "errors" extension: {"type": ..., "errors": {"name": "..."}}. */
    @Override
    public Map<String, String> fieldErrors(Response validationResponse) {
        return validationResponse.jsonPath().getMap("errors", String.class, String.class);
    }
}
