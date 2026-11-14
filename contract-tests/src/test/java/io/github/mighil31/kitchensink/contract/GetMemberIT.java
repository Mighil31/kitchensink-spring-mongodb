package io.github.mighil31.kitchensink.contract;

import io.github.mighil31.kitchensink.contract.support.ContractTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;

class GetMemberIT extends ContractTest {

    @Test
    void unknownId_returns404() {
        given(api)
                .when().get("/members/{id}", "999999999")
                .then().statusCode(404);
    }

    @ParameterizedTest(name = "GET /members/{0} -> 404")
    @ValueSource(strings = {"abc", "-1", "1.5", "0x1"})
    void malformedId_returns404(String id) {
        given(api)
                .when().get("/members/{id}", id)
                .then().statusCode(404);
    }
}
