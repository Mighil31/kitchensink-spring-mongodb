package io.github.mighil31.kitchensink.migration;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Updates.combine;
import static com.mongodb.client.model.Updates.setOnInsert;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.UpdateOptions;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import org.springframework.context.annotation.Profile;

/**
 * Legacy's {@code import.sql} row, for local development only (ADR 0005). Never runs without the
 * {@code dev} profile, so production starts empty. He keeps legacy id 0 as {@code legacyId}, the
 * one example of ADR 0002's mapping. Upsert by email so an existing row is left alone.
 */
@Profile("dev")
@TargetSystem(id = FlamingockConfig.TARGET_SYSTEM)
@Change(id = "dev-seed-john-smith", author = "kitchensink", transactional = false)
public class _0005__SeedJohnSmith {

    static final String EMAIL = "john.smith@mailinator.com";

    @Apply
    public void apply(MongoDatabase database) {
        database.getCollection("members")
                .updateOne(
                        eq("email", EMAIL),
                        combine(
                                setOnInsert("name", "John Smith"),
                                setOnInsert("phoneNumber", "2125551212"),
                                setOnInsert("legacyId", 0L)),
                        new UpdateOptions().upsert(true));
    }

    @Rollback
    public void rollback(MongoDatabase database) {
        database.getCollection("members").deleteOne(eq("legacyId", 0L));
    }
}
