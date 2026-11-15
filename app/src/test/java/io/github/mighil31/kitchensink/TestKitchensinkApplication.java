package io.github.mighil31.kitchensink;

import org.springframework.boot.SpringApplication;

/** Runs the app locally against a Testcontainers MongoDB, with dev seed data: mvn -pl app spring-boot:test-run */
public class TestKitchensinkApplication {

    public static void main(String[] args) {
        SpringApplication.from(KitchensinkApplication::main)
                .with(TestcontainersConfiguration.class)
                .withAdditionalProfiles("dev")
                .run(args);
    }
}
