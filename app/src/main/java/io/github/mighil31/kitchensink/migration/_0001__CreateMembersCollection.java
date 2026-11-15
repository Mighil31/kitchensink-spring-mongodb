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
 * Creates {@code members} with a {@code $jsonSchema} validator: a server-side backstop for the
 * Bean Validation rules (ADR 0011).
 *
 * <p>The validator is never stricter than the app layer, or a request the API accepts would fail
 * at write time as a 500. So it mirrors legacy's actual behaviour, quirks included:
 *
 * <ul>
 *   <li>{@code name}: only ASCII 0-9 are banned (legacy accepts other digits, gap B19).
 *       {@code maxLength} counts code points, Bean Validation counts UTF-16 units, so the
 *       validator is looser for emoji, never stricter.
 *   <li>{@code email}: non-empty only; {@code @Email} is lenient and a copied regex would drift.
 *   <li>{@code phoneNumber}: length only, because legacy accepts {@code +123456789},
 *       {@code 1.23456789E9} and non-ASCII digits (gap B18).
 * </ul>
 *
 * <p>Created with the validator in one step so the app's {@code readWrite} user can run it;
 * {@code collMod} would need {@code dbAdmin}. Tighten later with a new change, never by editing
 * this one.
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
