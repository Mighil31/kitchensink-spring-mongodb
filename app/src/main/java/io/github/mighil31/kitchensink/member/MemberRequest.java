package io.github.mighil31.kitchensink.member;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /rest/members} and of the registration form. The constraints are legacy's
 * {@code Member} annotations, plus one fix: a name of only spaces is rejected (gap B3). Odd phone
 * numbers (B18) are still accepted, as on legacy.
 *
 * <p>No {@code id}: a client can't choose one (gap B4). Unknown JSON fields are ignored.
 */
public record MemberRequest(
        @NotNull
        @Size(min = 1, max = 25)
        @Pattern(regexp = "[^0-9]*", message = "{name_validation_message}")
        @Pattern(regexp = NOT_ONLY_WHITESPACE, message = "{jakarta.validation.constraints.NotBlank.message}")
        String name,

        @NotNull @NotEmpty @Email String email,

        @NotNull @Size(min = 10, max = 12) @Digits(fraction = 0, integer = 12)
        String phoneNumber) {

    /**
     * Gap B3. Not {@code @NotBlank}: for {@code ""} it would fail alongside {@code @Size} and the
     * reported message would vary (gap B16). This lets {@code ""} through, so {@code ""} still gets
     * legacy's size message, and rejects strings of whitespace only. {@code (?U)}: Unicode
     * whitespace, so non-breaking spaces count too. {@code (?s)}: {@code .} also matches newlines.
     */
    static final String NOT_ONLY_WHITESPACE = "(?sU)|.*\\S.*";
}
