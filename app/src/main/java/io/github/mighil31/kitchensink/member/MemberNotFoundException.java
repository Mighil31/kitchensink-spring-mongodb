package io.github.mighil31.kitchensink.member;

class MemberNotFoundException extends RuntimeException {

    MemberNotFoundException() {
        super("Member not found");
    }
}
