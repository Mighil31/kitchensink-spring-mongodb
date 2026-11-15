package io.github.mighil31.kitchensink.member;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mighil31.kitchensink.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(MemberRestController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "API_CLIENT")
class ApiRoutingExceptionHandlerTest {

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    MemberRepository repository;

    @MockitoBean
    MemberRegistration registration;

    @Test
    void unsupportedMethodIs405ProblemWithAllowHeader() {
        MvcTestResult result = mvc.delete().uri("/rest/members").exchange();

        // The order of the methods isn't fixed.
        assertThat(result.getResponse().getHeader(HttpHeaders.ALLOW)).contains("GET", "POST");
        assertThat(result)
                .hasStatus(HttpStatus.METHOD_NOT_ALLOWED)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 405, "detail": "Method 'DELETE' is not supported.", "instance": "/rest/members"}
                        """);
    }

    @Test
    void unknownApiPathIs404ProblemWithoutInternals() {
        assertThat(mvc.get().uri("/rest/nope"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"status": 404, "detail": "No such endpoint", "instance": "/rest/nope"}
                        """);
    }

    /**
     * Outside the API the exception goes back to Spring's default handling: a plain 404 here, which
     * the real server turns into its HTML error page (MockMvc doesn't render error pages).
     */
    @Test
    void errorsOutsideTheApiAreNotProblemDetail() {
        assertThat(mvc.get().uri("/nope").accept(MediaType.TEXT_HTML))
                .hasStatus(HttpStatus.NOT_FOUND)
                .body()
                .isEmpty();
    }
}
