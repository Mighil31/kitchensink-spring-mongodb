package io.github.mighil31.kitchensink.contract;

import io.github.mighil31.kitchensink.contract.support.ContractTest;
import io.github.mighil31.kitchensink.contract.support.TestMembers;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

class ListMembersIT extends ContractTest {

    @Test
    void members_areSortedByName_caseSensitive() {
        String suffix = " " + TestMembers.randomLetters(8);
        for (String first : List.of("bob", "Alice", "alice", "Bob")) {
            createNamed(first + suffix);
        }

        List<String> names = given(api)
                .when().get("/members")
                .then().statusCode(200)
                .extract().jsonPath().getList("name", String.class);

        assertThat(names)
                .filteredOn(name -> name.endsWith(suffix))
                .containsExactly("Alice" + suffix, "Bob" + suffix, "alice" + suffix, "bob" + suffix);
    }

    private void createNamed(String name) {
        Map<String, Object> member = TestMembers.validMember();
        member.put("name", name);
        given(api)
                .body(member)
                .when().post("/members")
                .then().statusCode(format.createdStatus());
    }
}
