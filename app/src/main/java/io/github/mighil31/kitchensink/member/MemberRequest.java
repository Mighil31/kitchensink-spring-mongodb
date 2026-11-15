package io.github.mighil31.kitchensink.member;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /rest/members} and of the registration form. Legacy's {@code Member}
 * constraints, with the fixes decided in ADR 0013:
 *
 * <ul>
 *   <li>{@code name}: letters of any script, spaces and {@code . ' ’ -} only. Legacy's rule only
 *       banned ASCII 0-9 while its message said "letters and spaces", so {@code <b>bold</b>},
 *       emoji and {@code Ana١} got through (gaps B11, B19). A name of only spaces is rejected
 *       (gap B3).
 *   <li>{@code phoneNumber}: ASCII digits only. Legacy's {@code @Digits} parses the text as a
 *       number, so {@code +123456789}, {@code 1.23456789E9} and Arabic-Indic digits passed (gap
 *       B18). {@code @Digits} stays so that what legacy rejected keeps legacy's message; the
 *       pattern only reports what legacy let through.
 * </ul>
 *
 * <p>A value breaking several rules reports one message, picked in a fixed order ({@link
 * FieldMessages}, gap B16). No {@code id}: a client can't choose one (gap B4). Unknown JSON fields
 * are ignored, as on legacy (gap B10).
 */
public record MemberRequest(
        @NotNull
        @Size(min = 1, max = 25)
        @Pattern(regexp = NAME_CHARACTERS, message = "{name_validation_message}")
        @Pattern(regexp = NOT_ONLY_WHITESPACE, message = "{jakarta.validation.constraints.NotBlank.message}")
        String name,

        @NotNull @NotEmpty @Email String email,

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
     * Gap B3. Not {@code @NotBlank}: for {@code ""} it would fail alongside {@code @Size}. This lets
     * {@code ""} through, so {@code ""} keeps legacy's size message, and rejects strings of
     * whitespace only. {@code (?U)}: Unicode whitespace, so non-breaking spaces count too. {@code
     * (?s)}: {@code .} also matches newlines.
     */
    static final String NOT_ONLY_WHITESPACE = "(?sU)|.*\\S.*";
}
