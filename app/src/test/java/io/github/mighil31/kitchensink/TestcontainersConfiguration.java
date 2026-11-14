package io.github.mighil31.kitchensink;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/** A throwaway MongoDB in Docker; @ServiceConnection points Spring at it automatically. */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Keep in step with compose.yaml. */
    public static final String MONGO_IMAGE = "mongo:8.0";

    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer(DockerImageName.parse(MONGO_IMAGE));
    }
}
