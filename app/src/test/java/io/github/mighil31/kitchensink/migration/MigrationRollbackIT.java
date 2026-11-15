package io.github.mighil31.kitchensink.migration;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.github.mighil31.kitchensink.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Each rollback undoes its apply. Runs against a scratch database so the app's own schema (used by
 * the other tests sharing this context) is untouched.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MigrationRollbackIT {

    @Autowired
    MongoClient mongoClient;

    MongoDatabase scratch;

    @BeforeEach
    void applyAll() {
        scratch = mongoClient.getDatabase("rollback_" + System.nanoTime());
        new _0001__CreateMembersCollection().apply(scratch);
        new _0002__MembersEmailUniqueIndex().apply(scratch);
        new _0003__MembersNameIndex().apply(scratch);
        new _0004__MembersLegacyIdIndex().apply(scratch);
        new _0005__SeedJohnSmith().apply(scratch);
    }

    @AfterEach
    void dropScratch() {
        scratch.drop();
    }

    @Test
    void rollbacksInReverseOrderUndoEverything() {
        new _0005__SeedJohnSmith().rollback(scratch);
        assertThat(scratch.getCollection("members").countDocuments()).isZero();

        new _0004__MembersLegacyIdIndex().rollback(scratch);
        assertThat(indexNames()).doesNotContain("legacyId_unique_partial");

        new _0003__MembersNameIndex().rollback(scratch);
        assertThat(indexNames()).doesNotContain("name_asc");

        new _0002__MembersEmailUniqueIndex().rollback(scratch);
        assertThat(indexNames()).containsExactly("_id_");

        new _0001__CreateMembersCollection().rollback(scratch);
        assertThat(scratch.listCollectionNames().into(new ArrayList<>())).doesNotContain("members");
    }

    private List<String> indexNames() {
        return scratch.getCollection("members")
                .listIndexes()
                .map(index -> index.getString("name"))
                .into(new ArrayList<>());
    }
}
