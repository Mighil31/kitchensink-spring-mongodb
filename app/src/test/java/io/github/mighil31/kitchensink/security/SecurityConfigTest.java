package io.github.mighil31.kitchensink.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import io.github.mighil31.kitchensink.member.Member;
import io.github.mighil31.kitchensink.member.MemberRegistration;
import io.github.mighil31.kitchensink.member.MemberRepository;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * ADR 0007 against the real filter chains, with the API client from test {@code config/application.properties}
 * (test-client / test-password).
 */
@WebMvcTest
@Import(SecurityConfig.class)
class SecurityConfigTest {

    static final String MEMBERS = "/rest/members";
    static final String VALID_MEMBER = """
            {"name":"Jane Doe","email":"jane@example.com","phoneNumber":"2125551234"}""";

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    MemberRepository repository;

    @MockitoBean
    MemberRegistration registration;

    @Nested
    class Api {

        @Test
        void withoutCredentialsIs401ProblemDetailWithChallenge() {
            assertThat(mvc.get().uri(MEMBERS))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"kitchensink\"")
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson()
                    .isLenientlyEqualTo("""
                            {"title":"Unauthorized","status":401,"detail":"Authentication required",
                             "instance":"/rest/members"}""");
        }

        @Test
        void wrongPasswordIs401WithTheSameGenericDetail() {
            assertThat(mvc.get().uri(MEMBERS).with(httpBasic("test-client", "wrong")))
                    .hasStatus(HttpStatus.UNAUTHORIZED)
                    .bodyJson()
                    .extractingPath("$.detail")
                    .isEqualTo("Authentication required");
        }

        @Test
        void unknownUserIs401() {
            assertThat(mvc.get().uri(MEMBERS).with(httpBasic("nobody", "test-password")))
                    .hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void everyEndpointNeedsCredentials() {
            assertThat(mvc.get().uri(MEMBERS + "/" + new ObjectId())).hasStatus(HttpStatus.UNAUTHORIZED);
            assertThat(postMember()).hasStatus(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void correctCredentialsReachTheApi() {
            given(repository.findAllByOrderByNameAsc()).willReturn(List.of());

            assertThat(mvc.get().uri(MEMBERS).with(httpBasic("test-client", "test-password")))
                    .hasStatusOk();
        }

        /** Stateless: credentials on every request, no session and so no CSRF token to send. */
        @Test
        void writesNeedNoCsrfTokenAndCreateNoSession() {
            given(registration.register(any()))
                    .willReturn(new Member(new ObjectId(), "Jane Doe", "jane@example.com", "2125551234", null));

            MvcTestResult result = mvc.post()
                    .uri(MEMBERS)
                    .with(httpBasic("test-client", "test-password"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_MEMBER)
                    .exchange();

            assertThat(result).hasStatus(HttpStatus.CREATED).doesNotContainHeader(HttpHeaders.SET_COOKIE);
            assertThat(result.getRequest().getSession(false)).isNull();
        }

        @Test
        void authenticatedWithoutTheRoleIs403ProblemDetail() {
            assertThat(mvc.get().uri(MEMBERS).with(user("someone").roles("OTHER")))
                    .hasStatus(HttpStatus.FORBIDDEN)
                    .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                    .bodyJson()
                    .extractingPath("$.detail")
                    .isEqualTo("Access denied");
        }

        @Test
        void apiResponsesCarryTheSecurityHeaders() {
            assertSecurityHeaders(mvc.get().uri(MEMBERS).exchange());
        }

        private MvcTestResult postMember() {
            return mvc.post()
                    .uri(MEMBERS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_MEMBER)
                    .exchange();
        }
    }

    @Nested
    class Ui {

        @Test
        void pageIsPublicWithCsrfTokenInTheForm() {
            given(repository.findAllByOrderByNameAsc()).willReturn(List.of());

            assertThat(mvc.get().uri("/"))
                    .hasStatusOk()
                    .bodyText()
                    .containsPattern("<input type=\"hidden\" name=\"_csrf\" value=\"[^\"]+\"");
        }

        @Test
        void formPostWithoutCsrfTokenIsRejected() {
            assertThat(mvc.post()
                            .uri("/")
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("name", "Jane Doe")
                            .param("email", "jane@example.com")
                            .param("phoneNumber", "2125551234"))
                    .hasStatus(HttpStatus.FORBIDDEN);
        }

        @Test
        void formPostWithCsrfTokenIsAccepted() {
            assertThat(mvc.post()
                            .uri("/")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                            .param("name", "Jane Doe")
                            .param("email", "jane@example.com")
                            .param("phoneNumber", "2125551234"))
                    .hasStatus(HttpStatus.FOUND)
                    .hasRedirectedUrl("/");
        }

        @Test
        void pageCarriesTheSecurityHeaders() {
            given(repository.findAllByOrderByNameAsc()).willReturn(List.of());

            assertSecurityHeaders(mvc.get().uri("/").exchange());
        }
    }

    private static void assertSecurityHeaders(MvcTestResult result) {
        assertThat(result)
                .hasHeader("Content-Security-Policy", SecurityConfig.CONTENT_SECURITY_POLICY)
                .hasHeader("Referrer-Policy", "strict-origin-when-cross-origin")
                .hasHeader("Permissions-Policy", SecurityConfig.PERMISSIONS_POLICY)
                .hasHeader("X-Content-Type-Options", "nosniff")
                .hasHeader("X-Frame-Options", "DENY");
    }
}
