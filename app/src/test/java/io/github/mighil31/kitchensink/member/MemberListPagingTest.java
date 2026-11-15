package io.github.mighil31.kitchensink.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import io.github.mighil31.kitchensink.security.SecurityConfig;
import java.util.List;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** Gap A3 (ADR 0013): paging only when asked for, same array shape, next page in a Link header. */
@WebMvcTest(MemberRestController.class)
@Import(SecurityConfig.class)
@WithMockUser(roles = "API_CLIENT")
class MemberListPagingTest {

    static final Member ANN = new Member(new ObjectId(), "Ann", "ann@example.com", "2125550000", null);
    static final Member BOB = new Member(new ObjectId(), "Bob", "bob@example.com", "2125550001", null);

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    MemberRepository repository;

    @MockitoBean
    MemberRegistration registration;

    @Test
    void withoutParametersTheWholeListComesBackAsBefore() {
        given(repository.findAllByOrderByNameAscIdAsc()).willReturn(List.of(ANN, BOB));

        assertThat(mvc.get().uri("/rest/members"))
                .hasStatusOk()
                .doesNotContainHeader(HttpHeaders.LINK)
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Ann", "Bob");
        then(repository).should(never()).findAllByOrderByNameAscIdAsc(any(PageRequest.class));
    }

    @Test
    void aPageWithMoreAfterItLinksToTheNextOne() {
        given(repository.findAllByOrderByNameAscIdAsc(PageRequest.of(0, 2)))
                .willReturn(new SliceImpl<>(List.of(ANN, BOB), PageRequest.of(0, 2), true));

        assertThat(mvc.get().uri("/rest/members?page=0&size=2"))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .hasHeader(HttpHeaders.LINK, "<http://localhost/rest/members?page=1&size=2>; rel=\"next\"")
                .bodyJson()
                .extractingPath("$[*].name")
                .asArray()
                .containsExactly("Ann", "Bob");
    }

    @Test
    void theLastPageHasNoNextLink() {
        given(repository.findAllByOrderByNameAscIdAsc(PageRequest.of(3, 2)))
                .willReturn(new SliceImpl<>(List.of(ANN), PageRequest.of(3, 2), false));

        assertThat(mvc.get().uri("/rest/members?page=3&size=2")).hasStatusOk().doesNotContainHeader(HttpHeaders.LINK);
    }

    /** Either parameter alone turns paging on; the other takes its default (page 0, size 20). */
    @ParameterizedTest
    @CsvSource({"page=2, 2, 20", "size=5, 0, 5"})
    void oneParameterIsEnough(String query, int page, int size) {
        given(repository.findAllByOrderByNameAscIdAsc(PageRequest.of(page, size)))
                .willReturn(new SliceImpl<>(List.of(), PageRequest.of(page, size), false));

        assertThat(mvc.get().uri("/rest/members?" + query)).hasStatusOk();
        then(repository).should().findAllByOrderByNameAscIdAsc(PageRequest.of(page, size));
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "size=0|size|must be greater than or equal to 1",
                "size=101|size|must be less than or equal to 100",
                "page=-1|page|must be greater than or equal to 0"
            })
    void outOfRangeParametersAre400Problem(String query, String parameter, String message) {
        assertThat(mvc.get().uri("/rest/members?" + query))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errors." + parameter)
                .isEqualTo(message);
    }

    @Test
    void nonNumericParameterIs400Problem() {
        assertThat(mvc.get().uri("/rest/members?page=two"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
    }
}
