package io.github.mighil31.kitchensink.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.jayway.jsonpath.JsonPath;
import io.github.mighil31.kitchensink.security.SecurityConfig;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/** Behaviour behind authentication; the authentication itself is in ApiSecurityTest. */
@WebMvcTest(MemberRestController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "API_CLIENT")
@ExtendWith(OutputCaptureExtension.class)
class MemberRestControllerTest {

    static final ObjectId JANE_ID = new ObjectId("65f0c0ffee0000000000abcd");
    static final Member JANE = new Member(JANE_ID, "Jane Doe", "jane@example.com", "2125551234", 42L);

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    MemberRepository repository;

    @MockitoBean
    MemberRegistration registration;

    @Test
    void listReturnsMembersInRepositoryOrder() {
        Member ann = new Member(new ObjectId(), "Ann", "ann@example.com", "2125550000", null);
        given(repository.findAllByOrderByNameAsc()).willReturn(List.of(ann, JANE));

        assertThat(mvc.get().uri("/rest/members"))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Ann", "Jane Doe");
    }

    @Test
    void emptyListIsAnEmptyArray() {
        given(repository.findAllByOrderByNameAsc()).willReturn(List.of());

        assertThat(mvc.get().uri("/rest/members")).hasStatusOk().bodyJson().isEqualTo("[]");
    }

    @Test
    void getReturnsMemberWithHexIdAndNoLegacyId() {
        given(repository.findById(JANE_ID)).willReturn(Optional.of(JANE));

        assertThat(mvc.get().uri("/rest/members/{id}", JANE_ID.toHexString()))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"id": "65f0c0ffee0000000000abcd", "name": "Jane Doe",
                         "email": "jane@example.com", "phoneNumber": "2125551234"}
                        """);
    }

    @Test
    void unknownIdIs404Problem() {
        given(repository.findById(any(ObjectId.class))).willReturn(Optional.empty());

        assertThat(mvc.get().uri("/rest/members/{id}", "000000000000000000000000"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"title": "Not Found", "status": 404, "detail": "Member not found",
                         "instance": "/rest/members/000000000000000000000000"}
                        """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-1", "1.5", "0x1", "0", "65f0c0ffee0000000000abc"})
    void malformedIdIs404ProblemWithoutQuerying(String id) {
        assertThat(mvc.get().uri("/rest/members/{id}", id))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Member not found");

        verify(repository, never()).findById(any());
    }

    /** The client sees a generic message and an id; the log has the same id and the cause. */
    @Test
    void unexpectedErrorIs500ProblemWithTraceIdAndNoInternals(CapturedOutput log) throws Exception {
        given(repository.findAllByOrderByNameAsc())
                .willThrow(new DataAccessResourceFailureException("Timed out connecting to mongo:27017"));

        MvcTestResult result = mvc.get().uri("/rest/members").exchange();

        assertThat(result)
                .hasStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"title": "Internal Server Error", "status": 500, "detail": "Unexpected error",
                         "instance": "/rest/members"}
                        """);
        assertThat(result).bodyText().doesNotContain("mongo", "Exception", "27017");
        String traceId = JsonPath.read(result.getResponse().getContentAsString(), "$.traceId");
        assertThat(traceId).matches("[0-9a-f]{32}");
        assertThat(log.getOut())
                .contains("traceId=" + traceId)
                .contains("DataAccessResourceFailureException: Timed out connecting to mongo:27017");
    }

    static final String VALID_BODY = """
            {"name": "Jane Doe", "email": "jane@example.com", "phoneNumber": "2125551234"}
            """;

    @Test
    void createReturns201WithLocationAndMember() {
        given(registration.register(any())).willReturn(JANE);

        assertThat(post(VALID_BODY))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/rest/members/65f0c0ffee0000000000abcd")
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo("65f0c0ffee0000000000abcd");
        then(registration).should().register(new MemberRequest("Jane Doe", "jane@example.com", "2125551234"));
    }

    @Test
    void clientCannotChooseTheId() {
        given(registration.register(any())).willReturn(JANE);

        assertThat(post("""
                        {"id": "000000000000000000000001", "name": "Jane Doe",
                         "email": "jane@example.com", "phoneNumber": "2125551234"}
                        """)).hasStatus(HttpStatus.CREATED);
        // MemberRequest has no id, so the client's value never reaches the service (gap B4).
        then(registration).should().register(new MemberRequest("Jane Doe", "jane@example.com", "2125551234"));
    }

    @Test
    void invalidBodyIs400ProblemWithFieldErrors() {
        assertThat(post("""
                        {"name": "R2D2", "email": "not-an-email", "phoneNumber": "123456789"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 400, "detail": "Validation failed", "instance": "/rest/members",
                         "errors": {"name": "must contain only letters and spaces",
                                    "email": "must be a well-formed email address",
                                    "phoneNumber": "size must be between 10 and 12"}}
                        """);
        verify(registration, never()).register(any());
    }

    @Test
    void missingFieldsReportNotNullEvenWhenAnotherRuleAlsoFails() {
        // A null email breaks @NotNull and @NotEmpty; legacy reports "must not be null".
        assertThat(post("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of(
                        "name", "must not be null",
                        "email", "must not be null",
                        "phoneNumber", "must not be null"));
    }

    /** Gap B3: legacy stored a name of only spaces. Unicode spaces and newlines count too. */
    @ParameterizedTest
    @ValueSource(strings = {" ", "   ", "\\t\\n", "\\u00a0\\u2003"})
    void nameOfOnlyWhitespaceIs400(String name) {
        assertThat(post("""
                        {"name": "%s", "email": "jane@example.com", "phoneNumber": "2125551234"}
                        """.formatted(name)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("name", "must not be blank"));
        verify(registration, never()).register(any());
    }

    /** The empty name keeps legacy's single message, which the contract suite checks. */
    @Test
    void emptyNameStillReportsOnlyTheSizeRule() {
        assertThat(post("""
                        {"name": "", "email": "jane@example.com", "phoneNumber": "2125551234"}
                        """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("name", "size must be between 1 and 25"));
    }

    @ParameterizedTest
    @ValueSource(strings = {" Jane", "Jane Doe ", "Mary Ann\\nLee"})
    void namesWithSomeWhitespaceAreStillAccepted(String name) {
        given(registration.register(any())).willReturn(JANE);

        assertThat(post("""
                        {"name": "%s", "email": "jane@example.com", "phoneNumber": "2125551234"}
                        """.formatted(name))).hasStatus(HttpStatus.CREATED);
    }

    @Test
    void messagesFollowAcceptLanguage() {
        assertThat(mvc.post()
                        .uri("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Accept-Language", "es")
                        .content("""
                                {"name": "R2D2", "email": "jane@example.com", "phoneNumber": "2125551234"}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors.name")
                .isEqualTo("debe contener sólo letras y espacios");
    }

    @Test
    void takenEmailIs409Problem() {
        given(registration.register(any()))
                .willThrow(new EmailAlreadyRegisteredException(new DuplicateKeyException("E11000")));

        assertThat(post(VALID_BODY))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 409, "detail": "Email taken", "errors": {"email": "Email taken"}}
                        """);
    }

    /** Gaps B5 and B17: legacy answered 500 with RESTEasy / JSON-B internals. */
    @ParameterizedTest
    @ValueSource(strings = {"{\"name\": ", "[]", "[{}]", "\"text\""})
    void unreadableBodyIs400ProblemWithoutInternals(String body) {
        assertThat(post(body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyText()
                .contains("\"detail\":\"Failed to read request\"")
                .doesNotContain("Exception", "jackson", "MemberRequest", "at [Source");
    }

    /** Gap B6: legacy answered 400 with Hibernate Validator's code HV000116. */
    @ParameterizedTest
    @ValueSource(strings = {"null", ""})
    void missingBodyIs400ProblemWithoutValidatorCodes(String body) {
        assertThat(post(body))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyText()
                .doesNotContain("HV0", "Exception", "MemberRequest");
    }

    @Test
    void nonJsonIs415() {
        assertThat(mvc.post()
                        .uri("/rest/members")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("{}"))
                .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    private MvcTestResult post(String json) {
        return mvc.post()
                .uri("/rest/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)
                .exchange();
    }
}
