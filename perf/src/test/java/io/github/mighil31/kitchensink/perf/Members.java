package io.github.mighil31.kitchensink.perf;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

/** Valid, never-repeating member bodies (unique email; letters-only name, as the name rule rejects digits). */
public final class Members {

    private Members() {}

    /** Endless feeder: each record holds a fresh JSON body under {@code memberBody}. */
    public static Iterator<Map<String, Object>> uniqueBodies() {
        return Stream.generate(() -> Map.<String, Object>of("memberBody", body(newEmail()))).iterator();
    }

    public static String newEmail() {
        return "perf-" + UUID.randomUUID() + "@example.com";
    }

    public static String body(String email) {
        return """
                {"name":"Perf %s","email":"%s","phoneNumber":"2125551234"}"""
                .formatted(randomLetters(10), email);
    }

    private static String randomLetters(int n) {
        StringBuilder sb = new StringBuilder(n);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }
}
