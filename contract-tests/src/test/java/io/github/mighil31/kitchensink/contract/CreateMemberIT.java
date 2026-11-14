package io.github.mighil31.kitchensink.contract;

import io.github.mighil31.kitchensink.contract.support.ContractTest;
import io.github.mighil31.kitchensink.contract.support.TestMembers;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class CreateMemberIT extends ContractTest {

    @Test
    void validMember_isCreated() {
        given(api)
                .body(TestMembers.validMember())
                .when().post("/members")
                .then().statusCode(format.createdStatus());
    }

    @Test
    void createdMember_canBeFetchedById() {
        Map<String, Object> member = TestMembers.validMember();
        String id = create(member);

        Map<String, Object> fetched = given(api)
                .when().get("/members/{id}", id)
                .then().statusCode(200)
                .extract().jsonPath().getMap("$");

        assertSameMember(fetched, member);
    }

    @Test
    void createdMember_appearsInList() {
        Map<String, Object> member = TestMembers.validMember();
        create(member);

        List<Map<String, Object>> members = given(api)
                .when().get("/members")
                .then().statusCode(200)
                .extract().jsonPath().getList("$");

        assertThat(members)
                .filteredOn(m -> member.get("email").equals(m.get("email")))
                .singleElement()
                .satisfies(listed -> assertSameMember(listed, member));
    }

    /** POSTs the member, asserts it was created and returns its id. */
    private String create(Map<String, Object> member) {
        Response response = given(api)
                .body(member)
                .when().post("/members")
                .then().statusCode(format.createdStatus())
                .extract().response();
        return format.idOfCreated(response, member);
    }

    private static void assertSameMember(Map<String, Object> actual, Map<String, Object> sent) {
        assertThat(actual)
                .containsEntry("name", sent.get("name"))
                .containsEntry("email", sent.get("email"))
                .containsEntry("phoneNumber", sent.get("phoneNumber"));
    }
}
