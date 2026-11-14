package io.github.mighil31.kitchensink.contract.support;

import java.util.Optional;

/**
 * Where the suite points. Set via Maven / system properties:
 * <ul>
 *   <li>{@code contract.baseUrl} – REST root, e.g. {@code http://localhost:8080/kitchensink/rest}</li>
 *   <li>{@code contract.user} – optional HTTP Basic user (blank = no auth, as on legacy)</li>
 *   <li>env {@code CONTRACT_PASSWORD} – password for that user (env only, never on the command line)</li>
 * </ul>
 */
public record ContractConfig(String baseUrl, Optional<Credentials> credentials) {

    public record Credentials(String user, String password) {}

    public static ContractConfig fromEnvironment() {
        String baseUrl = System.getProperty("contract.baseUrl", "http://localhost:8080/kitchensink/rest");
        String user = System.getProperty("contract.user", "");

        Optional<Credentials> credentials = Optional.empty();
        if (!user.isBlank()) {
            String password = System.getenv("CONTRACT_PASSWORD");
            if (password == null || password.isEmpty()) {
                throw new IllegalStateException("contract.user is set but env CONTRACT_PASSWORD is not");
            }
            credentials = Optional.of(new Credentials(user, password));
        }
        return new ContractConfig(stripTrailingSlash(baseUrl), credentials);
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
