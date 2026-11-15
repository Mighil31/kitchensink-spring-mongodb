package io.github.mighil31.kitchensink.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The one API client allowed on {@code /rest/**} (ADR 0007). Set from the environment:
 * {@code KITCHENSINK_API_USERNAME} and {@code KITCHENSINK_API_PASSWORD}. There is no default, so the
 * app doesn't start without them. The password may be given already hashed ({@code {bcrypt}$2a$...})
 * so the plain text never has to reach the server.
 */
@Validated
@ConfigurationProperties("kitchensink.api")
record ApiClientProperties(
        @NotBlank String username, @NotBlank String password) {

    @Override
    public String toString() {
        return "ApiClientProperties[username=" + username + ", password=****]";
    }
}
