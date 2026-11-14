package io.github.mighil31.kitchensink.contract.support;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Map;

/**
 * Representation differences between targets (status codes, id shape, where ids come from).
 * Implementations only read and represent responses; they never decide behaviour.
 */
public interface ApiFormat {

    /** Status returned for a successful create. */
    int createdStatus();

    /** Id of the member created by {@code createResponse}, as a string. */
    String idOfCreated(Response createResponse, Map<String, Object> requestBody);

    /** An id that is well-formed for this target but belongs to no member. */
    String unknownId();

    static ApiFormat forName(String name, RequestSpecification api) {
        return switch (name) {
            case "legacy" -> new LegacyFormat(api);
            case "v2" -> new V2Format();
            default -> throw new IllegalArgumentException("Unknown contract.format: " + name);
        };
    }
}
