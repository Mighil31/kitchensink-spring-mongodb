package io.github.mighil31.kitchensink.contract;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.github.mighil31.kitchensink.contract.support.ContractTest;
import io.github.mighil31.kitchensink.contract.support.TestMembers;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ValidationIT extends ContractTest {

    private static final String NOT_NULL = "must not be null";
    private static final String NAME_SIZE = "size must be between 1 and 25";
    private static final String NAME_PATTERN = "must contain only letters and spaces";
    private static final String EMAIL_EMPTY = "must not be empty";
    private static final String EMAIL_FORMAT = "must be a well-formed email address";
    private static final String PHONE_SIZE = "size must be between 10 and 12";
    private static final String PHONE_DIGITS = "numeric value out of bounds (<12 digits>.<0 digits> expected)";

    private static final Map<String, String> MESSAGES = Map.of(
            "NAME_SIZE",
            NAME_SIZE,
            "NAME_PATTERN",
            NAME_PATTERN,
            "EMAIL_EMPTY",
            EMAIL_EMPTY,
            "EMAIL_FORMAT",
            EMAIL_FORMAT,
            "PHONE_SIZE",
            PHONE_SIZE,
            "PHONE_DIGITS",
            PHONE_DIGITS);

    @Test
    void missingFields_areAllReported() {
        Map<String, String> errors = postExpectingValidationErrors(Map.of());

        assertThat(errors)
                .containsOnly(
                        Map.entry("name", NOT_NULL), Map.entry("email", NOT_NULL), Map.entry("phoneNumber", NOT_NULL));
    }

    static Stream<Arguments> invalidNames() {
        return Stream.of(
                arguments("", Set.of(NAME_SIZE)),
                arguments("abcdefghijklmnopqrstuvwxyz", Set.of(NAME_SIZE)),
                arguments("R2D2", Set.of(NAME_PATTERN)));
    }

    @ParameterizedTest(name = "name {0} -> 400")
    @MethodSource("invalidNames")
    void invalidName_isRejected(String name, Set<String> allowedMessages) {
        assertOnlyFieldRejected("name", name, allowedMessages);
    }

    @ParameterizedTest(name = "name {0} -> created")
    @ValueSource(strings = {"a", "abcdefghijklmnopqrstuvwxy", "Mary-Jane O'Neil"})
    void boundaryName_isAccepted(String name) {
        postExpectingCreated(memberWith("name", name));
    }

    static Stream<Arguments> invalidEmails() {
        return Stream.of(arguments("", Set.of(EMAIL_EMPTY)), arguments("not-an-email", Set.of(EMAIL_FORMAT)));
    }

    @ParameterizedTest(name = "email {0} -> 400")
    @MethodSource("invalidEmails")
    void invalidEmail_isRejected(String email, Set<String> allowedMessages) {
        assertOnlyFieldRejected("email", email, allowedMessages);
    }

    @Test
    void unusualEmail_isAccepted() {
        // No top-level domain. Local part is random so reruns don't hit the duplicate check.
        postExpectingCreated(memberWith("email", TestMembers.randomLetters(12) + "@b"));
    }

    static Stream<Arguments> invalidPhones() {
        return Stream.of(
                arguments("123456789", Set.of(PHONE_SIZE)),
                arguments("12345abcde", Set.of(PHONE_DIGITS)),
                // Break both @Size and @Digits; legacy reports one of them at random (gap B16).
                arguments("1234567890123", Set.of(PHONE_SIZE, PHONE_DIGITS)),
                arguments("", Set.of(PHONE_SIZE, PHONE_DIGITS)));
    }

    @ParameterizedTest(name = "phone {0} -> 400")
    @MethodSource("invalidPhones")
    void invalidPhone_isRejected(String phone, Set<String> allowedMessages) {
        assertOnlyFieldRejected("phoneNumber", phone, allowedMessages);
    }

    @ParameterizedTest(name = "phone {0} -> created")
    @ValueSource(strings = {"1234567890", "123456789012"})
    void boundaryPhone_isAccepted(String phone) {
        postExpectingCreated(memberWith("phoneNumber", phone));
    }

    @ParameterizedTest(name = "{0} {1} -> {2} ({4})")
    @CsvFileSource(resources = "/llm-generated-validation-cases.csv")
    void generatedCase(String field, String value, String expected, String messageKeys, String probes) {
        String input = value.replace("{rand}", TestMembers.randomLetters(10));
        if (expected.equals("created")) {
            postExpectingCreated(memberWith(field, input));
        } else {
            Set<String> allowed =
                    Arrays.stream(messageKeys.split("\\|")).map(MESSAGES::get).collect(Collectors.toSet());
            assertOnlyFieldRejected(field, input, allowed);
        }
    }

    private void assertOnlyFieldRejected(String field, String value, Set<String> allowedMessages) {
        Map<String, String> errors = postExpectingValidationErrors(memberWith(field, value));

        assertThat(errors).containsOnlyKeys(field);
        assertThat(errors.get(field)).isIn(allowedMessages);
    }

    private static Map<String, Object> memberWith(String field, Object value) {
        Map<String, Object> member = TestMembers.validMember();
        member.put(field, value);
        return member;
    }

    private Map<String, String> postExpectingValidationErrors(Map<String, Object> body) {
        return format.fieldErrors(given(api)
                .body(body)
                .when()
                .post("/members")
                .then()
                .statusCode(400)
                .extract()
                .response());
    }

    private void postExpectingCreated(Map<String, Object> body) {
        given(api).body(body).when().post("/members").then().statusCode(format.createdStatus());
    }
}
