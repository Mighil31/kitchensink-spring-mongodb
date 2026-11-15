package io.github.mighil31.kitchensink.member;

/** The unique {@code email} index rejected a registration. */
public class EmailAlreadyRegisteredException extends RuntimeException {

    EmailAlreadyRegisteredException(Throwable cause) {
        // No email in the message: it ends up in logs.
        super("A member with this email is already registered", cause);
    }
}
