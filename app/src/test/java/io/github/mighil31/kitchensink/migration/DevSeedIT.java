package io.github.mighil31.kitchensink.migration;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mighil31.kitchensink.TestcontainersConfiguration;
import io.github.mighil31.kitchensink.member.Member;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

/** With the dev profile, legacy's seed member is there, once, keeping his legacy id. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("dev")
class DevSeedIT {

    @Autowired
    MongoTemplate mongoTemplate;

    @Test
    void seedsJohnSmithOnce() {
        List<Member> members = mongoTemplate.findAll(Member.class);

        assertThat(members).singleElement().satisfies(john -> {
            assertThat(john.id()).isNotNull();
            assertThat(john.name()).isEqualTo("John Smith");
            assertThat(john.email()).isEqualTo("john.smith@mailinator.com");
            assertThat(john.phoneNumber()).isEqualTo("2125551212");
            assertThat(john.legacyId()).isZero();
        });
    }

    @Test
    void seedIsIdempotent() {
        new _0005__SeedJohnSmith().apply(mongoTemplate.getDb());

        assertThat(mongoTemplate.getCollection(Member.COLLECTION).countDocuments())
                .isEqualTo(1);
    }
}
