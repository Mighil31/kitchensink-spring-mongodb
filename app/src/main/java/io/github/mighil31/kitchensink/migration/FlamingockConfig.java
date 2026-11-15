package io.github.mighil31.kitchensink.migration;

import com.mongodb.client.MongoClient;
import io.flamingock.api.annotations.EnableFlamingock;
import io.flamingock.api.annotations.Stage;
import io.flamingock.store.mongodb.sync.MongoDBSyncAuditStore;
import io.flamingock.targetsystem.mongodb.sync.MongoDBSyncTargetSystem;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;

/**
 * Wires Flamingock to the app's MongoDB through the plain driver (ADR 0011). Changes in this
 * package run in name order ({@code _0001__…}) before the web server starts, with a lock, and
 * are recorded in an audit collection so each runs once.
 */
@Configuration(proxyBeanMethods = false)
@EnableFlamingock(stages = @Stage(name = "schema", location = "io.github.mighil31.kitchensink.migration"))
public class FlamingockConfig {

    /** Referenced by every change's {@code @TargetSystem}. */
    public static final String TARGET_SYSTEM = "kitchensink-mongodb";

    @Bean
    MongoDBSyncTargetSystem mongoTargetSystem(MongoClient mongoClient, MongoDatabaseFactory databaseFactory) {
        // Same client and database as the rest of the app (spring.mongodb.uri).
        String database = databaseFactory.getMongoDatabase().getName();
        return new MongoDBSyncTargetSystem(TARGET_SYSTEM, mongoClient, database);
    }

    @Bean
    MongoDBSyncAuditStore auditStore(MongoDBSyncTargetSystem targetSystem) {
        return MongoDBSyncAuditStore.from(targetSystem);
    }
}
