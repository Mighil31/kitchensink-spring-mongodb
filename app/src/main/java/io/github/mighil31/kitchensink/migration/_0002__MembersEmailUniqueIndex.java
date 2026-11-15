package io.github.mighil31.kitchensink.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;

/**
 * Unique {@code email}: the database, not a check-then-insert, guarantees uniqueness (gap B1).
 *
 * <p>Case-sensitive for now, like legacy. The Phase 5 fix for B2 stores emails trimmed and
 * lower-cased, so this same index then enforces case-insensitive uniqueness without a collation
 * (ADR 0011).
 */
@TargetSystem(id = FlamingockConfig.TARGET_SYSTEM)
@Change(id = "members-email-unique-index", author = "kitchensink", transactional = false)
public class _0002__MembersEmailUniqueIndex {

    static final String INDEX = "email_unique";

    @Apply
    public void apply(MongoDatabase database) {
        database.getCollection("members")
                .createIndex(
                        Indexes.ascending("email"),
                        new IndexOptions().name(INDEX).unique(true));
    }

    @Rollback
    public void rollback(MongoDatabase database) {
        database.getCollection("members").dropIndex(INDEX);
    }
}
