package io.github.mighil31.kitchensink.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;

/**
 * Creates {@code members} with a {@code $jsonSchema} validator, a backstop for writes that bypass
 * the app (ADR 0011).
 *
 * <p>It must never be stricter than the app's validation, or a request the API accepts would fail
 * at write time as a 500. So it checks only what legacy enforced (types, lengths, no ASCII digits in
 * names), which also lets migrated legacy data load. {@code maxLength} counts code points, Bean
 * Validation UTF-16 units, so emoji make the validator looser, never stricter.
 *
 * <p>Created together with the collection because the app's {@code readWrite} user can't run
 * {@code collMod} (that needs {@code dbAdmin}).
 */
@TargetSystem(id = FlamingockConfig.TARGET_SYSTEM)
@Change(id = "create-members-collection", author = "kitchensink", transactional = false)
public class _0001__CreateMembersCollection {

    static final Document SCHEMA = Document.parse("""
            {
              "bsonType": "object",
              "required": ["name", "email", "phoneNumber"],
              "properties": {
                "_id": { "bsonType": "objectId" },
                "name": { "bsonType": "string", "minLength": 1, "maxLength": 25, "pattern": "^[^0-9]*$" },
                "email": { "bsonType": "string", "minLength": 1 },
                "phoneNumber": { "bsonType": "string", "minLength": 10, "maxLength": 12 },
                "legacyId": { "bsonType": "long" }
              }
            }
            """);

    @Apply
    public void apply(MongoDatabase database) {
        database.createCollection(
                "members",
                new CreateCollectionOptions()
                        .validationOptions(new ValidationOptions()
                                .validator(new Document("$jsonSchema", SCHEMA))
                                .validationLevel(ValidationLevel.STRICT)
                                .validationAction(ValidationAction.ERROR)));
    }

    @Rollback
    public void rollback(MongoDatabase database) {
        database.getCollection("members").drop();
    }
}
