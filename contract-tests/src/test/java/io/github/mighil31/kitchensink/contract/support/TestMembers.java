package io.github.mighil31.kitchensink.contract.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test data. The target's database can't be reset between tests, so every member must be unique:
 * <ul>
 *   <li>emails carry a UUID</li>
 *   <li>names get a random <b>letters-only</b> suffix, because the name rule rejects any digit</li>
 * </ul>
 * Bodies are plain mutable maps so a test can null out, remove or add fields freely.
 */
public final class TestMembers {

    public static final String VALID_PHONE = "2125551234";

    private TestMembers() {}

    /** A body that passes every validation rule and won't collide with existing data. */
    public static Map<String, Object> validMember() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", uniqueName("Contract"));
        body.put("email", uniqueEmail());
        body.put("phoneNumber", VALID_PHONE);
        return body;
    }

    public static String uniqueEmail() {
        return "ct-" + UUID.randomUUID() + "@example.com";
    }

    /** {@code prefix + " " + 8 random lowercase letters}. Prefix must be digit-free and at most 16 chars (name max is 25). */
    public static String uniqueName(String prefix) {
        if (prefix.length() > 16 || prefix.chars().anyMatch(Character::isDigit)) {
            throw new IllegalArgumentException("prefix must be digit-free and <= 16 chars: " + prefix);
        }
        return prefix + " " + randomLetters(8);
    }

    public static String randomLetters(int n) {
        StringBuilder sb = new StringBuilder(n);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int i = 0; i < n; i++) {
            sb.append((char) ('a' + random.nextInt(26)));
        }
        return sb.toString();
    }
}
