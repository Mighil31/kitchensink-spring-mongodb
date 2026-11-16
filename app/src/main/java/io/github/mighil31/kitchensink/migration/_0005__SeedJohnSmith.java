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
 * Legacy's seed member, {@code dev} profile only, so production starts empty (ADR 0005). Upsert by
 * email so an existing member is left alone.
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
