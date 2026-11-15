package io.github.mighil31.kitchensink.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@WebMvcTest(MemberRestController.class)
class MemberRestControllerTest {

    static final ObjectId JANE_ID = new ObjectId("65f0c0ffee0000000000abcd");
    static final Member JANE = new Member(JANE_ID, "Jane Doe", "jane@example.com", "2125551234", 42L);

    @Autowired
    MockMvcTester mvc;

    @MockitoBean
    MemberRepository repository;

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
    void unknownIdIs404WithEmptyBody() {
        given(repository.findById(any(ObjectId.class))).willReturn(Optional.empty());

        assertThat(mvc.get().uri("/rest/members/{id}", "000000000000000000000000"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .body()
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "-1", "1.5", "0x1", "0", "65f0c0ffee0000000000abc"})
    void malformedIdIs404WithoutQuerying(String id) {
        assertThat(mvc.get().uri("/rest/members/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);

        verify(repository, never()).findById(any());
    }
}
