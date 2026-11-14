package io.github.mighil31.kitchensink.contract;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.mighil31.kitchensink.contract.support.ContractTest;
import io.github.mighil31.kitchensink.contract.support.TestMembers;
import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DuplicateEmailIT extends ContractTest {

    @Test
    void sameEmail_isRejectedWithConflict_andNotStored() {
        Map<String, Object> original = TestMembers.validMember();
        post(original).then().statusCode(format.createdStatus());

        Map<String, Object> duplicate = TestMembers.validMember();
        duplicate.put("email", original.get("email"));
        post(duplicate).then().statusCode(409);

        List<Map<String, Object>> members = given(api)
                .when()
                .get("/members")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");

        assertThat(members)
                .filteredOn(m -> original.get("email").equals(m.get("email")))
                .singleElement()
                .satisfies(m -> assertThat(m).containsEntry("name", original.get("name")));
    }

    private Response post(Map<String, Object> body) {
        return given(api).body(body).when().post("/members");
    }
}
