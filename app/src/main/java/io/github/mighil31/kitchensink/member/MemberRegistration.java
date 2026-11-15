package io.github.mighil31.kitchensink.member;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Replaces legacy's {@code @Stateless MemberRegistration}.
 *
 * <p>Legacy checked for the email, then inserted: two concurrent requests could both pass the check
 * (gap B1). Here there is no check; the unique {@code email} index decides, atomically, and the
 * loser gets {@link EmailAlreadyRegisteredException}. Legacy's CDI event is not ported (ADR 0012).
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
