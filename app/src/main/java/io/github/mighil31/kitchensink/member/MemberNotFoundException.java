package io.github.mighil31.kitchensink.member;

/** No member with that id, or the id isn't an ObjectId at all; both are 404 (ADR 0002, ADR 0003). */
class MemberNotFoundException extends RuntimeException {

    MemberNotFoundException() {
        super("Member not found");
    }
}
