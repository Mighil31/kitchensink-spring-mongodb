package io.github.mighil31.kitchensink.member;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /rest/members} and of the registration form (rules: ADR 0013). No {@code id}
 * field, so a client can't choose one. Unknown JSON fields are ignored.
 *
 * <p>{@code @Digits} alone accepts {@code +123456789} and {@code 1.23456789E9} (it parses the text
 * as a number), hence the extra pattern. {@code @Digits} is kept so that values legacy rejected keep
 * legacy's message, which the contract suite checks.
 *
 * <p>A value can break several rules; {@link FieldMessages} picks which message is shown.
 */
public record MemberRequest(
        @NotNull
        @Size(min = 1, max = 25)
        @Pattern(regexp = NAME_CHARACTERS, message = "{name_validation_message}")
        @Pattern(regexp = NOT_ONLY_WHITESPACE, message = "{jakarta.validation.constraints.NotBlank.message}")
        String name,

        @NotNull
        @NotEmpty
        @Email
        @Pattern(regexp = EMAIL_DOMAIN, message = "{jakarta.validation.constraints.Email.message}")
        String email,

        @NotNull
        @Size(min = 10, max = 12)
        @Digits(fraction = 0, integer = 12)
        @Pattern(regexp = "[0-9]*", message = "{phone_validation_message}")
        String phoneNumber) {

    /**
     * Letters ({@code \p{L}}) with their combining marks ({@code \p{M}}, for accents typed as two
     * code points), spaces, and the punctuation real names use: O'Brien, O’Brien, Jean-Luc, St. John.
     * {@code *}, not {@code +}: the empty name is left to {@code @Size}, so it reports one message.
     */
    static final String NAME_CHARACTERS = "[\\p{L}\\p{M} .'’-]*";

    /**
     * Rejects whitespace-only names. Not {@code @NotBlank}: for {@code ""} that would fire alongside
     * {@code @Size}; this lets {@code ""} through so it gets only the size message. {@code (?U)}:
     * Unicode whitespace (non-breaking spaces too). {@code (?s)}: {@code .} also matches newlines.
     */
    static final String NOT_ONLY_WHITESPACE = "(?sU)|.*\\S.*";

    /**
     * The domain needs a dot and a final label of 2+ characters, so {@code a@gmail} and {@code
     * a@b.c} are rejected (gap B12, ADR 0014); an IP literal ({@code a@[127.0.0.1]}) stays valid.
     * {@code @Email} alone accepts dotless domains, as the RFC does. The message is {@code @Email}'s,
     * so whichever of the two rules fails, the client sees the same text. {@code ""} passes, left to
     * {@code @NotEmpty}.
     */
    static final String EMAIL_DOMAIN = "(?s)|.*@(?:\\[.*]|.+\\.[^.@]{2,})";
}
