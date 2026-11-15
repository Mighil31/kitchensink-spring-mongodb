package io.github.mighil31.kitchensink.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * ADR 0007, option B. The REST API needs HTTP Basic; the UI stays public, as on legacy, with CSRF
 * protection on its form. Both get the same security headers.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ApiClientProperties.class)
public class SecurityConfig {

    static final String API_CLIENT = "API_CLIENT";

    /** No inline scripts or styles anywhere, so nothing needs 'unsafe-inline'. */
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'self'; object-src 'none'; base-uri 'self'; " + "form-action 'self'; frame-ancestors 'none'";

    static final String PERMISSIONS_POLICY = "camera=(), microphone=(), geolocation=(), payment=()";

    /**
     * Every endpoint, reads too: member data is personal data. Stateless (credentials on each
     * request, no session cookie), so there is nothing for CSRF to abuse and it's off here.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
        ProblemDetailSecurityHandler problems = new ProblemDetailSecurityHandler();
        return http.securityMatcher("/rest/**")
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(API_CLIENT))
                .httpBasic(basic ->
                        basic.realmName(ProblemDetailSecurityHandler.REALM).authenticationEntryPoint(problems))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(problems).accessDeniedHandler(problems))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .csrf(csrf -> csrf.disable())
                .headers(SecurityConfig::headers)
                .build();
    }

    /** The registration page, static files, Actuator health and the error page: open to everyone. */
    @Bean
    @Order(2)
    SecurityFilterChain webSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(SecurityConfig::headers)
                .build();
    }

    /**
     * On top of Spring Security's defaults (nosniff, X-Frame-Options DENY, no-cache, HSTS on HTTPS
     * requests only).
     */
    private static void headers(HeadersConfigurer<HttpSecurity> headers) {
        headers.contentSecurityPolicy(csp -> csp.policyDirectives(CONTENT_SECURITY_POLICY))
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .permissionsPolicyHeader(permissions -> permissions.policy(PERMISSIONS_POLICY));
    }

    /** Stores {@code {bcrypt}...}; checks any {@code {id}} prefix, so the algorithm can change later. */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** The single API client, held in memory. Real accounts are out of scope (ADR 0007). */
    @Bean
    UserDetailsService apiClients(ApiClientProperties client, PasswordEncoder encoder) {
        String password =
                client.password().startsWith("{bcrypt}") ? client.password() : encoder.encode(client.password());
        return new InMemoryUserDetailsManager(User.withUsername(client.username())
                .password(password)
                .roles(API_CLIENT)
                .build());
    }
}
