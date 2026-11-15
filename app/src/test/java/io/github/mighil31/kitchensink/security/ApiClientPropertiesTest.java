package io.github.mighil31.kitchensink.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

class ApiClientPropertiesTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ApiClientProperties.class)
    static class PropertiesOnly {}

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesOnly.class);

    /** No default password: forgetting to set one stops the app instead of leaving the API open. */
    @Test
    void appDoesNotStartWithoutCredentials() {
        runner.run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("kitchensink.api.username=client")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void bindsCredentialsAndHidesThePassword() {
        runner.withPropertyValues("kitchensink.api.username=client", "kitchensink.api.password=s3cret")
                .run(context -> {
                    ApiClientProperties client = context.getBean(ApiClientProperties.class);
                    assertThat(client.password()).isEqualTo("s3cret");
                    assertThat(client.toString()).contains("client").doesNotContain("s3cret");
                });
    }

    @Test
    void plainPasswordIsStoredAsBcrypt() {
        UserDetails user = clientWithPassword("s3cret");

        assertThat(user.getPassword()).startsWith("{bcrypt}$2");
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("s3cret", user.getPassword()))
                .isTrue();
        assertThat(user.getAuthorities()).extracting("authority").containsExactly("ROLE_API_CLIENT");
    }

    /** Production can keep only the hash in its secret store. */
    @Test
    void preHashedPasswordIsUsedAsIs() {
        String hash = PasswordEncoderFactories.createDelegatingPasswordEncoder().encode("s3cret");

        assertThat(clientWithPassword(hash).getPassword()).isEqualTo(hash);
    }

    /** Performance attribution run only (ADR 0010): authentication without bcrypt's cost. */
    @Test
    void noopPasswordIsUsedAsIsAndStillChecked() {
        UserDetails user = clientWithPassword("{noop}s3cret");

        assertThat(user.getPassword()).isEqualTo("{noop}s3cret");
        assertThat(PasswordEncoderFactories.createDelegatingPasswordEncoder().matches("s3cret", user.getPassword()))
                .isTrue();
    }

    /** Only the two known prefixes count; anything else is a plain password and gets hashed. */
    @Test
    void otherBracePrefixesAreTreatedAsPlainPasswords() {
        assertThat(clientWithPassword("{sha256}abc").getPassword()).startsWith("{bcrypt}$2");
    }

    private static UserDetails clientWithPassword(String password) {
        SecurityConfig config = new SecurityConfig();
        return config.apiClients(new ApiClientProperties("client", password), config.passwordEncoder())
                .loadUserByUsername("client");
    }
}
