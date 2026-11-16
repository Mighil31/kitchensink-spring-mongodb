package io.github.mighil31.kitchensink.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The API client ({@code KITCHENSINK_API_USERNAME}, {@code KITCHENSINK_API_PASSWORD}). No defaults:
 * the app doesn't start without them, rather than leaving the API open.
 */
@Validated
@ConfigurationProperties("kitchensink.api")
record ApiClientProperties(
        @NotBlank String username, @NotBlank String password) {

    // Masked: the properties object can end up in logs and Actuator output.
    @Override
    public String toString() {
        return "ApiClientProperties[username=" + username + ", password=****]";
    }
}
