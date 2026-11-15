package io.github.mighil31.kitchensink.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;

/**
 * Unique {@code legacyId}, partial: only members carried over from legacy have one (ADR 0002).
 * A plain unique index would treat every missing value as null and allow just one new member.
 */
@TargetSystem(id = FlamingockConfig.TARGET_SYSTEM)
@Change(id = "members-legacy-id-index", author = "kitchensink", transactional = false)
public class _0004__MembersLegacyIdIndex {

    static final String INDEX = "legacyId_unique_partial";

    @Apply
    public void apply(MongoDatabase database) {
        database.getCollection("members")
                .createIndex(
                        Indexes.ascending("legacyId"),
                        new IndexOptions()
                                .name(INDEX)
                                .unique(true)
                                .partialFilterExpression(Filters.exists("legacyId")));
    }

    @Rollback
    public void rollback(MongoDatabase database) {
        database.getCollection("members").dropIndex(INDEX);
    }
}
