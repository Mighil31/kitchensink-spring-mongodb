package io.github.mighil31.kitchensink.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;

/**
 * Unique {@code email}. The index is case-sensitive; uniqueness ignores case because emails are
 * stored lower-cased ({@code Member.normalizeEmail}, {@code _0006}), with no collation (ADR 0011).
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
