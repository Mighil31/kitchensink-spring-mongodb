package io.github.mighil31.kitchensink.migration;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import org.bson.Document;

/**
 * {@code {name: 1, _id: 1}} replaces {@code name_asc} (ADR 0013). Names aren't unique, so paging by
 * name alone could repeat or skip members across pages; {@code _id} breaks ties. Created before the
 * old one is dropped, so the sort always has an index.
 */
@TargetSystem(id = FlamingockConfig.TARGET_SYSTEM)
@Change(id = "members-name-id-index", author = "kitchensink", transactional = false)
public class _0007__MembersNameIdIndex {

    static final String INDEX = "name_id_asc";

    @Apply
    public void apply(MongoDatabase database) {
        MongoCollection<Document> members = database.getCollection("members");
        members.createIndex(Indexes.ascending("name", "_id"), new IndexOptions().name(INDEX));
        members.dropIndex(_0003__MembersNameIndex.INDEX);
    }

    @Rollback
    public void rollback(MongoDatabase database) {
        MongoCollection<Document> members = database.getCollection("members");
        members.createIndex(Indexes.ascending("name"), new IndexOptions().name(_0003__MembersNameIndex.INDEX));
        members.dropIndex(INDEX);
    }
}
