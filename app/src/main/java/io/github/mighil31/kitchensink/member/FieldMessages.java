package io.github.mighil31.kitchensink.member;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.validation.FieldError;

/**
 * One message per field, the same one every time (gap B16, ADR 0013).
 *
 * <p>A value can break several rules at once: an empty phone number breaks both {@code @Size} and
 * {@code @Digits}. The validator reports them in no fixed order, and legacy showed whichever came
 * first, so the same request could get different messages. Here the rule that ranks first in
 * {@link #PRIORITY} wins: a missing value first, then length, then content. Ties (the same rule
 * twice) go to the alphabetically first message, so even those are stable.
 */
final class FieldMessages {

    /** Constraint names as Spring reports them (the annotation's simple name). Unknown ones rank last. */
    static final List<String> PRIORITY = List.of("NotNull", "NotEmpty", "Size", "Digits", "Pattern", "Email");

    private FieldMessages() {}

    /** Field name to its single message, fields in the order of their first error. */
    static Map<String, String> firstPerField(List<FieldError> errors, Function<FieldError, String> message) {
        Map<String, FieldError> chosen = new LinkedHashMap<>();
        Comparator<FieldError> order =
                Comparator.comparingInt(FieldMessages::rank).thenComparing(message, Comparator.naturalOrder());
        for (FieldError error : errors) {
            chosen.merge(
                    error.getField(),
                    error,
                    (current, candidate) -> order.compare(candidate, current) < 0 ? candidate : current);
        }
        Map<String, String> messages = new LinkedHashMap<>();
        chosen.forEach((field, error) -> messages.put(field, message.apply(error)));
        return messages;
    }

    private static int rank(FieldError error) {
        int index = PRIORITY.indexOf(error.getCode());
        return index < 0 ? PRIORITY.size() : index;
    }
}
