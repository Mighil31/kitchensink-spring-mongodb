package io.github.mighil31.kitchensink.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

    static final Member JANE =
            new Member(new ObjectId("65f0c0ffee0000000000abcd"), "Jane Doe", "jane@example.com", "2125551234", null);

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    MemberRepository repository;

    @MockitoBean
    MemberRegistration registration;

    @Test
    void pageShowsFormAndEmptyList() {
        given(repository.findAllByOrderByNameAsc()).willReturn(List.of());

        assertThat(mvc.get().uri("/"))
                .hasStatusOk()
                .hasViewName("index")
                .bodyText()
                .contains("Member Registration", "name=\"name\"", "name=\"email\"", "name=\"phoneNumber\"")
                .contains("No registered members.");
    }

    @Test
    void pageListsMembersWithRestLinks() {
        given(repository.findAllByOrderByNameAsc()).willReturn(List.of(JANE));

        assertThat(mvc.get().uri("/"))
                .hasStatusOk()
                .bodyText()
                .contains("Jane Doe", "jane@example.com", "2125551234")
                .contains("href=\"/rest/members/65f0c0ffee0000000000abcd\"", "href=\"/rest/members\"")
                .doesNotContain("No registered members.");
    }

    /** Gap S9: stored markup is shown as text, never rendered. */
    @Test
    void memberDataIsEscaped() {
        Member markup = new Member(new ObjectId(), "<b>bold</b>", "x@example.com", "2125551234", null);
        given(repository.findAllByOrderByNameAsc()).willReturn(List.of(markup));

        assertThat(mvc.get().uri("/"))
                .bodyText()
                .contains("&lt;b&gt;bold&lt;/b&gt;")
                .doesNotContain("<b>bold</b>");
    }

    /** Gap B14: Post/Redirect/Get, so a refresh doesn't submit again. */
    @Test
    void validRegistrationRedirectsWithFlashMessage() {
        assertThat(submit("Jane Doe", "jane@example.com", "2125551234"))
                .hasStatus(HttpStatus.FOUND)
                .hasRedirectedUrl("/")
                .flash()
                .containsEntry("registered", true);
        then(registration).should().register(new MemberRequest("Jane Doe", "jane@example.com", "2125551234"));
    }

    @Test
    void pageAfterRedirectSaysRegistered() {
        given(repository.findAllByOrderByNameAsc()).willReturn(List.of(JANE));

        assertThat(mvc.get().uri("/").flashAttr("registered", true)).bodyText().contains("Registered!");
    }

    @Test
    void invalidRegistrationShowsFieldErrorsAndKeepsInput() {
        assertThat(submit("R2D2", "not-an-email", "123"))
                .hasStatusOk()
                .bodyText()
                .contains("must contain only letters and spaces")
                .contains("must be a well-formed email address")
                .contains("size must be between 10 and 12")
                .contains("value=\"R2D2\"", "value=\"not-an-email\"");
        then(registration).should(never()).register(any());
    }

    @Test
    void emptyFieldsAreRejectedLikeLegacy() {
        assertThat(submit("", "", ""))
                .hasStatusOk()
                .bodyText()
                .contains("size must be between 1 and 25", "must not be empty")
                // "" breaks both phone rules; which one shows is not fixed (gap B16, open).
                .containsAnyOf("size must be between 10 and 12", "numeric value out of bounds");
        then(registration).should(never()).register(any());
    }

    /** Gap B7: legacy showed the database's exception text; this shows a field message. */
    @Test
    void takenEmailIsAFieldErrorNotExceptionText() {
        given(registration.register(any()))
                .willThrow(new EmailAlreadyRegisteredException(new DuplicateKeyException("E11000 duplicate key")));

        assertThat(submit("Jane Doe", "jane@example.com", "2125551234"))
                .hasStatusOk()
                .bodyText()
                .contains("Email taken")
                .doesNotContain("E11000", "Exception");
    }

    @Test
    void pageFollowsAcceptLanguage() {
        assertThat(mvc.post()
                        .uri("/")
                        .header("Accept-Language", "es")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "R2D2")
                        .param("email", "jane@example.com")
                        .param("phoneNumber", "2125551234"))
                .hasStatusOk()
                .bodyText()
                .contains("Registro de Usuarios", "debe contener sólo letras y espacios");
    }

    private MvcTestResult submit(String name, String email, String phone) {
        return mvc.post()
                .uri("/")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("name", name)
                .param("email", email)
                .param("phoneNumber", phone)
                .exchange();
    }
}
