package io.github.mighil31.kitchensink.migration;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.BulkWriteOptions;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.UpdateOneModel;
import com.mongodb.client.model.Updates;
import io.flamingock.api.annotations.Apply;
import io.flamingock.api.annotations.Change;
import io.flamingock.api.annotations.Rollback;
import io.flamingock.api.annotations.TargetSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bson.Document;

/**
 * Gap B2: existing emails lower-cased, as the app now stores new ones, so the unique index on
 * {@code email} also covers addresses that differ only by case (ADR 0011).
 *
 * <ul>
 *   <li>Done in Java, not with MongoDB's {@code $toLower}, which is only defined for ASCII; this
 *       way the result is exactly what the app writes ({@code toLowerCase(Locale.ROOT)}). The rule
 *       is copied here, not called from the app: a migration must keep doing what it did when it
 *       was written.
 *   <li>Two members whose emails differ only by case can't both keep theirs: the unique index
 *       rejects the second update. Those are left as they are, everything else is updated, and the
 *       change fails, naming only how many, never the addresses (personal data in logs). The app
 *       doesn't start until someone decides which member keeps the address; Flamingock then runs
 *       the change again, which is safe because lower-casing twice changes nothing.
 *   <li>Unordered bulk writes in batches, so one conflict doesn't stop the rest and a large
 *       collection isn't updated one round trip per document.
 * </ul>
 */
@TargetSystem(id = FlamingockConfig.TARGET_SYSTEM)
@Change(id = "members-lowercase-emails", author = "kitchensink", transactional = false)
public class _0006__LowercaseMemberEmails {

    static final int BATCH_SIZE = 1_000;
    private static final int DUPLICATE_KEY = 11000;

    @Apply
    public void apply(MongoDatabase database) {
        MongoCollection<Document> members = database.getCollection("members");
        List<UpdateOneModel<Document>> batch = new ArrayList<>(BATCH_SIZE);
        int conflicts = 0;
        for (Document member : members.find().projection(Projections.include("email"))) {
            String email = member.getString("email");
            String lower = email.toLowerCase(Locale.ROOT);
            if (!lower.equals(email)) {
                batch.add(new UpdateOneModel<>(Filters.eq("_id", member.get("_id")), Updates.set("email", lower)));
            }
            if (batch.size() == BATCH_SIZE) {
                conflicts += write(members, batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            conflicts += write(members, batch);
        }
        if (conflicts > 0) {
            throw new IllegalStateException(conflicts + " member email(s) differ from another member's only by"
                    + " letter case. Merge or change them, then restart to finish this migration.");
        }
    }

    /** Returns how many updates the unique index rejected; any other write error is rethrown. */
    private static int write(MongoCollection<Document> members, List<UpdateOneModel<Document>> batch) {
        try {
            members.bulkWrite(batch, new BulkWriteOptions().ordered(false));
            return 0;
        } catch (MongoBulkWriteException e) {
            if (e.getWriteErrors().stream().anyMatch(error -> error.getCode() != DUPLICATE_KEY)) {
                throw e;
            }
            return e.getWriteErrors().size();
        }
    }

    /** Nothing to undo: the original casing isn't kept, and lower-case emails are valid either way. */
    @Rollback
    public void rollback(MongoDatabase database) {}
}
