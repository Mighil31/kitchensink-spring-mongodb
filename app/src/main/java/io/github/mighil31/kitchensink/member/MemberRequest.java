package io.github.mighil31.kitchensink.member;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /rest/members}. The constraints are legacy's {@code Member} annotations,
 * unchanged, so the API accepts and rejects exactly what legacy did; the known gaps (blank names
 * B3, odd phone numbers B18) are fixed in Phase 5.
 *
 * <p>No {@code id}: a client can't choose one (gap B4). Unknown JSON fields are ignored.
 */
public record MemberRequest(
        @NotNull @Size(min = 1, max = 25) @Pattern(regexp = "[^0-9]*", message = "{name_validation_message}")
        String name,

        @NotNull @NotEmpty @Email String email,

        @NotNull @Size(min = 10, max = 12) @Digits(fraction = 0, integer = 12)
        String phoneNumber) {}
