package io.github.mighil31.kitchensink.member;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MemberTest {

    @Test
    void newMembersGetTheirEmailLowerCased() {
        assertThat(Member.of("Jane Doe", "Jane.Doe@Example.COM", "2125551234").email())
                .isEqualTo("jane.doe@example.com");
    }

    /** In Turkish, "I".toLowerCase() is a dotless ı; the stored email mustn't depend on the server. */
    @Test
    void lowerCasingIgnoresTheServerLocale() {
        Locale before = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            assertThat(Member.normalizeEmail("INFO@EXAMPLE.COM")).isEqualTo("info@example.com");
        } finally {
            Locale.setDefault(before);
        }
    }
}
