package io.github.mighil31.kitchensink.migration;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;

/**
 * {@code name} index for the member list's sort. No collation: binary order matches legacy's
 * case-sensitive order (ADR 0009), and a collation index is only used by queries that pass the
 * same collation.
 */
@TargetSystem(id = FlamingockConfig.TARGET_SYSTEM)
@Change(id = "members-name-index", author = "kitchensink", transactional = false)
public class _0003__MembersNameIndex {

    static final String INDEX = "name_asc";

    @Apply
    public void apply(MongoDatabase database) {
        database.getCollection("members").createIndex(Indexes.ascending("name"), new IndexOptions().name(INDEX));
    }

    @Rollback
    public void rollback(MongoDatabase database) {
        database.getCollection("members").dropIndex(INDEX);
    }
}
