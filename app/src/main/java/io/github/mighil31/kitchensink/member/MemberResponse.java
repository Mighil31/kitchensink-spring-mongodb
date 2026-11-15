package io.github.mighil31.kitchensink.member;

/**
 * A member as the REST API returns it. The id is the ObjectId's 24-hex string (ADR 0002);
 * {@code legacyId} stays internal.
 */
public record MemberResponse(String id, String name, String email, String phoneNumber) {

    static MemberResponse from(Member member) {
        return new MemberResponse(member.id().toHexString(), member.name(), member.email(), member.phoneNumber());
    }
}
