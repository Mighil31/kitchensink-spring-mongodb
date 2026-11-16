package io.github.mighil31.kitchensink.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * No "does this email exist?" check before inserting: two concurrent requests could both pass it.
 * The unique {@code email} index decides atomically, and the loser gets {@link
 * EmailAlreadyRegisteredException}.
 */
@Service
public class MemberRegistration {

    private static final Logger log = LoggerFactory.getLogger(MemberRegistration.class);

    private final MemberRepository repository;

    MemberRegistration(MemberRepository repository) {
        this.repository = repository;
    }

    public Member register(MemberRequest request) {
        try {
            Member saved = repository.insert(Member.of(request.name(), request.email(), request.phoneNumber()));
            log.info("Registered member {}", saved.id());
            return saved;
        } catch (DuplicateKeyException e) {
            // The only unique key an API insert can break: _id is generated, legacyId is absent.
            throw new EmailAlreadyRegisteredException(e);
        }
    }
}
