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
import org.junit.jupiter.params.provider.CsvSource;
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

/** Behaviour behind authentication; the authentication itself is in SecurityConfigTest. */
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
        given(repository.findAllByOrderByNameAscIdAsc()).willReturn(List.of(ann, JANE));

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
        given(repository.findAllByOrderByNameAscIdAsc()).willReturn(List.of());

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

    /** The client sees a generic message and the request id; the log line has the same id and the cause. */
    @Test
    void unexpectedErrorIs500ProblemWithRequestIdAndNoInternals(CapturedOutput log) throws Exception {
        given(repository.findAllByOrderByNameAscIdAsc())
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
        String requestId = JsonPath.read(result.getResponse().getContentAsString(), "$.requestId");
        assertThat(requestId).matches("[0-9a-f]{32}");
        assertThat(result).hasHeader("X-Request-Id", requestId);
        assertThat(log.getOut())
                .contains("[" + requestId + "]")
                .contains("DataAccessResourceFailureException: Timed out connecting to mongo:27017");
    }

    static final String NAME_RULE = "must contain only letters, spaces, hyphens, apostrophes and periods";

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
                         "errors": {"name": "must contain only letters, spaces, hyphens, apostrophes and periods",
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

    /** Gap B3: legacy stored a name of only spaces. */
    @ParameterizedTest
    @ValueSource(strings = {" ", "   "})
    void nameOfOnlySpacesIs400(String name) {
        assertThat(post("""
                        {"name": "%s", "email": "jane@example.com", "phoneNumber": "2125551234"}
                        """.formatted(name)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("name", "must not be blank"));
        verify(registration, never()).register(any());
    }

    /**
     * Other whitespace isn't a name character (only the plain space is), so tabs, newlines and
     * non-breaking spaces get the character rule's message. They also break the blank rule; the
     * fixed order picks the same message every time (gap B16).
     */
    @ParameterizedTest
    @ValueSource(strings = {"\\t\\n", "\\u00a0\\u2003", "Mary Ann\\nLee"})
    void otherWhitespaceIsNotANameCharacter(String name) {
        assertThat(post("""
                        {"name": "%s", "email": "jane@example.com", "phoneNumber": "2125551234"}
                        """.formatted(name)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("name", NAME_RULE));
    }

    /** Gaps B11 and B19: legacy only banned ASCII 0-9, so markup, emoji and other digits got through. */
    @ParameterizedTest
    @ValueSource(strings = {"<b>bold</b>", "Ana\\u0661", "Ana\\uff11", "\\ud83d\\ude00", "Jane_Doe", "R2D2", "Jane!"})
    void nameWithOtherCharactersIs400(String name) {
        assertThat(post("""
                        {"name": "%s", "email": "jane@example.com", "phoneNumber": "2125551234"}
                        """.formatted(name)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("name", NAME_RULE));
    }

    /** Letters of any script, with accents typed as one or two code points, and name punctuation. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "Zoë Ångström",
                "Jose\\u0301 Pe\\u0301rez",
                "O'Brien",
                "O\\u2019Brien",
                "Jean-Luc",
                "St. John",
                "李小龙",
                "Владимир"
            })
    void namesInAnyScriptAreAccepted(String name) {
        given(registration.register(any())).willReturn(JANE);

        assertThat(post("""
                        {"name": "%s", "email": "jane@example.com", "phoneNumber": "2125551234"}
                        """.formatted(name))).hasStatus(HttpStatus.CREATED);
    }

    /** Gap B18: legacy's @Digits parsed the text as a number, so these were stored as phone numbers. */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "+123456789",
                "-123456789",
                "1.23456789E9",
                "\\u0661\\u0662\\u0663\\u0664\\u0665\\u0666\\u0667\\u0668\\u0669\\u0660"
            })
    void phoneThatOnlyParsesAsANumberIs400(String phone) {
        assertThat(post("""
                        {"name": "Jane Doe", "email": "jane@example.com", "phoneNumber": "%s"}
                        """.formatted(phone)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("phoneNumber", "must contain only the digits 0-9"));
    }

    /** Gap B12: legacy's @Email accepted a domain without a dot or with a one-letter last label. */
    @ParameterizedTest
    @ValueSource(strings = {"jane@gmail", "jane@localhost", "jane@example.c", "jane@b"})
    void emailWithoutTopLevelDomainIs400(String email) {
        assertThat(post("""
                        {"name": "Jane Doe", "email": "%s", "phoneNumber": "2125551234"}
                        """.formatted(email)))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("email", "must be a well-formed email address"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"jane@example.com", "jane+tag@mail.example.co.uk", "jane@[127.0.0.1]", "jane@exämple.de"})
    void emailWithTopLevelDomainIsAccepted(String email) {
        given(registration.register(any())).willReturn(JANE);

        assertThat(post("""
                        {"name": "Jane Doe", "email": "%s", "phoneNumber": "2125551234"}
                        """.formatted(email))).hasStatus(HttpStatus.CREATED);
    }

    /**
     * Gap B16: legacy picked one of the broken rules at random. Now a fixed order decides (length
     * before content), so repeating the request always gives the same message. What legacy
     * rejected keeps one of legacy's messages.
     */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "''|size must be between 10 and 12",
                "1234567890123|size must be between 10 and 12",
                "12345abcde|numeric value out of bounds (<12 digits>.<0 digits> expected)",
                "+123456789012|size must be between 10 and 12"
            })
    void fieldBreakingSeveralRulesAlwaysGetsTheSameMessage(String phone, String message) {
        for (int i = 0; i < 20; i++) {
            assertThat(post("""
                            {"name": "Jane Doe", "email": "jane@example.com", "phoneNumber": "%s"}
                            """.formatted(phone)))
                    .bodyJson()
                    .extractingPath("$.errors.phoneNumber")
                    .isEqualTo(message);
        }
    }

    /** Gap B10, decided: unknown fields are ignored, as on legacy, so clients sending extras keep working. */
    @Test
    void unknownFieldsAreIgnored() {
        given(registration.register(any())).willReturn(JANE);

        assertThat(post("""
                        {"name": "Jane Doe", "email": "jane@example.com", "phoneNumber": "2125551234",
                         "nickname": "JD", "admin": true}
                        """)).hasStatus(HttpStatus.CREATED);
        then(registration).should().register(new MemberRequest("Jane Doe", "jane@example.com", "2125551234"));
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
    @ValueSource(strings = {" Jane", "Jane Doe ", "Mary  Ann Lee"})
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
                .isEqualTo("sólo debe contener letras, espacios, guiones, apóstrofos y puntos");
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
