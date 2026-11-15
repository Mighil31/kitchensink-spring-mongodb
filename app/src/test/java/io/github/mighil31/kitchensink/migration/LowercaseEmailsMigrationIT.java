package io.github.mighil31.kitchensink.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import io.github.mighil31.kitchensink.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Gap B2 backfill, on data the app could never write now: mixed-case emails, as legacy stored them.
 * A scratch database with the schema up to the email index, so the app's own data is untouched.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LowercaseEmailsMigrationIT {

    @Autowired
    MongoClient mongoClient;

    MongoDatabase scratch;
    MongoCollection<Document> members;
    final _0006__LowercaseMemberEmails migration = new _0006__LowercaseMemberEmails();

    @BeforeEach
    void schemaWithLegacyData() {
        scratch = mongoClient.getDatabase("lowercase_" + System.nanoTime());
        new _0001__CreateMembersCollection().apply(scratch);
        new _0002__MembersEmailUniqueIndex().apply(scratch);
        members = scratch.getCollection("members");
    }

    @AfterEach
    void dropScratch() {
        scratch.drop();
    }

    @Test
    void lowerCasesEmailsIncludingNonAscii() {
        insert("Jane.Doe@Example.COM", "already@lower.com", "ÜSER@EXÄMPLE.COM");

        migration.apply(scratch);

        assertThat(emails()).containsExactlyInAnyOrder("jane.doe@example.com", "already@lower.com", "üser@exämple.com");
    }

    /** Fails on the clash, says how many but not who, and still updates everything else. */
    @Test
    void emailsDifferingOnlyByCaseStopTheMigrationWithoutNamingThem() {
        insert("jane@example.com", "JANE@example.com", "Other@Example.com");

        assertThatThrownBy(() -> migration.apply(scratch))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("1 member email(s) differ")
                .hasMessageNotContainingAny("jane", "JANE");

        assertThat(emails()).containsExactlyInAnyOrder("jane@example.com", "JANE@example.com", "other@example.com");
    }

    /** After someone resolves the clash, the next start runs the change again and it completes. */
    @Test
    void runsAgainCleanlyOnceTheClashIsResolved() {
        insert("jane@example.com", "JANE@example.com");
        assertThatThrownBy(() -> migration.apply(scratch)).isInstanceOf(IllegalStateException.class);

        members.updateOne(
                Filters.eq("email", "JANE@example.com"),
                new Document("$set", new Document("email", "Jane2@example.com")));
        migration.apply(scratch);

        assertThat(emails()).containsExactlyInAnyOrder("jane@example.com", "jane2@example.com");
    }

    @Test
    void handlesMoreThanOneBatch() {
        int count = _0006__LowercaseMemberEmails.BATCH_SIZE * 2 + 500;
        insert(IntStream.range(0, count)
                .mapToObj(i -> "Member" + i + "@Example.com")
                .toArray(String[]::new));

        migration.apply(scratch);

        assertThat(members.countDocuments(Filters.regex("email", "[A-Z]"))).isZero();
        assertThat(members.countDocuments()).isEqualTo(count);
    }

    private void insert(String... emails) {
        List<Document> docs = new ArrayList<>();
        for (String email : emails) {
            docs.add(
                    new Document("name", "Legacy Member").append("email", email).append("phoneNumber", "2125551234"));
        }
        members.insertMany(docs);
    }

    private List<String> emails() {
        return members.find().map(doc -> doc.getString("email")).into(new ArrayList<>());
    }
}
