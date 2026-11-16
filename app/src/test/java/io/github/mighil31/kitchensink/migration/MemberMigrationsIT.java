package io.github.mighil31.kitchensink.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import io.github.mighil31.kitchensink.TestcontainersConfiguration;
import io.github.mighil31.kitchensink.member.Member;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;

/** The migrations ran at startup (default profile, as in production) and the schema behaves. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MemberMigrationsIT {

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void emptyMembers() {
        // deleteMany, not drop: dropping would also drop the indexes under test.
        members().deleteMany(new Document());
    }

    @Test
    void auditRecordsSchemaChangesButNotTheDevSeed() {
        List<String> applied = mongoTemplate
                .getCollection("flamingockAuditLog")
                .find(new Document("state", "APPLIED"))
                .map(entry -> entry.getString("changeId"))
                .into(new ArrayList<>());

        assertThat(applied)
                .containsExactlyInAnyOrder(
                        "create-members-collection",
                        "members-email-unique-index",
                        "members-name-index",
                        "members-legacy-id-index",
                        "members-lowercase-emails",
                        "members-name-id-index");
    }

    @Test
    void noSeedDataWithoutDevProfile() {
        assertThat(members().countDocuments()).isZero();
    }

    @Test
    void emailIndexIsUnique() {
        Document index = index("email_unique");

        assertThat(index.get("key", Document.class)).isEqualTo(new Document("email", 1));
        assertThat(index.getBoolean("unique")).isTrue();
        assertThat(index).doesNotContainKey("collation");
    }

    @Test
    void nameIndexHasIdTieBreakAndNoCollation() {
        Document index = index("name_id_asc");

        assertThat(index.get("key", Document.class)).isEqualTo(new Document("name", 1).append("_id", 1));
        assertThat(index).doesNotContainKeys("unique", "collation");
        assertThat(members().listIndexes().map(i -> i.getString("name")).into(new ArrayList<>()))
                .as("_0007 replaced name_asc")
                .doesNotContain("name_asc");
    }

    @Test
    void legacyIdIndexIsUniqueAndPartial() {
        Document index = index("legacyId_unique_partial");

        assertThat(index.get("key", Document.class)).isEqualTo(new Document("legacyId", 1));
        assertThat(index.getBoolean("unique")).isTrue();
        assertThat(index.get("partialFilterExpression", Document.class))
                .isEqualTo(Document.parse("{legacyId: {$exists: true}}"));
    }

    @Test
    void validatorIsStrictAndRejectsWrites() {
        Document options = mongoTemplate
                .getDb()
                .listCollections()
                .filter(new Document("name", "members"))
                .first()
                .get("options", Document.class);

        assertThat(options.getString("validationLevel")).isEqualTo("strict");
        assertThat(options.getString("validationAction")).isEqualTo("error");
        assertThat(options.get("validator", Document.class)).containsKey("$jsonSchema");
    }

    @Test
    void duplicateEmailIsRejected() {
        mongoTemplate.insert(Member.of("Jane Doe", "jane@example.com", "2125551234"));

        assertThatThrownBy(() -> mongoTemplate.insert(Member.of("Other Jane", "jane@example.com", "2125550000")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    /**
     * Gap B2 design (ADR 0011): the index compares case-sensitively; case-insensitive uniqueness
     * comes from the app storing emails lower-cased. Writes that bypass the app aren't covered.
     */
    @Test
    void indexIsCaseSensitiveSoTheAppNormalizesEmails() {
        members()
                .insertOne(new Document("name", "Jane Doe")
                        .append("email", "jane@example.com")
                        .append("phoneNumber", "2125551234"));
        members()
                .insertOne(new Document("name", "Jane Doe")
                        .append("email", "JANE@example.com")
                        .append("phoneNumber", "2125551234"));
        assertThat(members().countDocuments()).isEqualTo(2);

        members().deleteMany(new Document());
        mongoTemplate.insert(Member.of("Jane Doe", "jane@example.com", "2125551234"));
        assertThatThrownBy(() -> mongoTemplate.insert(Member.of("Jane Doe", "JANE@example.com", "2125551234")))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void manyMembersWithoutLegacyIdAreAllowed() {
        mongoTemplate.insert(Member.of("Ann", "ann@example.com", "2125551234"));
        mongoTemplate.insert(Member.of("Bob", "bob@example.com", "2125551234"));

        assertThat(members().countDocuments()).isEqualTo(2);
        assertThat(members().find().first()).doesNotContainKey("legacyId");
    }

    @Test
    void duplicateLegacyIdIsRejected() {
        mongoTemplate.insert(new Member(null, "Ann", "ann@example.com", "2125551234", 7L));

        assertThatThrownBy(() -> mongoTemplate.insert(new Member(null, "Bob", "bob@example.com", "2125551234", 7L)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void insertReturnsMemberWithGeneratedObjectId() {
        Member saved = mongoTemplate.insert(Member.of("Ann", "ann@example.com", "2125551234"));

        assertThat(saved.id()).isNotNull();
        assertThat(mongoTemplate.findById(saved.id(), Member.class)).isEqualTo(saved);
    }

    /** Values legacy accepts must never be blocked by the validator (it may be looser, not stricter). */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "name:Zoë Ångström",
                "name:Ana١", // Arabic-Indic digit, gap B19
                "name:😀😀😀😀😀😀😀😀😀😀😀😀", // 24 UTF-16 units, under @Size(max = 25)
                "name:abcdefghijklmnopqrstuvwxy", // exactly 25
                "email:a@b", // gap B12
                "phoneNumber:+123456789", // gap B18
                "phoneNumber:-123456789",
                "phoneNumber:1.23456789E9",
                "phoneNumber:١٢٣٤٥٦٧٨٩٠",
                "phoneNumber:123456789012"
            })
    void validatorAcceptsWhatLegacyAccepts(String fieldAndValue) {
        Document member = memberWith(fieldAndValue);

        members().insertOne(member);

        assertThat(members().countDocuments()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "name:",
                "name:Ann1",
                "name:abcdefghijklmnopqrstuvwxyz", // 26
                "email:",
                "phoneNumber:123456789", // 9
                "phoneNumber:1234567890123", // 13
                "legacyId:" // wrong type: string, not long
            })
    void validatorRejectsWhatBeanValidationRejects(String fieldAndValue) {
        Document member = memberWith(fieldAndValue);

        assertThatThrownBy(() -> members().insertOne(member))
                .isInstanceOfSatisfying(
                        MongoWriteException.class,
                        e -> assertThat(e.getError().getCode()).isEqualTo(121));
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "email", "phoneNumber"})
    void validatorRequiresField(String field) {
        Document member = memberWith("name:Ann");
        member.remove(field);

        assertThatThrownBy(() -> members().insertOne(member))
                .isInstanceOfSatisfying(
                        MongoWriteException.class,
                        e -> assertThat(e.getError().getCode()).isEqualTo(121));
    }

    private static Document memberWith(String fieldAndValue) {
        String[] parts = fieldAndValue.split(":", 2);
        Document member = new Document(Map.of("name", "Ann", "email", "ann@example.com", "phoneNumber", "2125551234"));
        member.put(parts[0], parts[1]);
        return member;
    }

    private MongoCollection<Document> members() {
        return mongoTemplate.getCollection(Member.COLLECTION);
    }

    private Document index(String name) {
        Map<String, Document> byName = members().listIndexes().into(new ArrayList<>()).stream()
                .collect(Collectors.toMap(index -> index.getString("name"), index -> index));
        assertThat(byName).containsKey(name);
        return byName.get(name);
    }
}
