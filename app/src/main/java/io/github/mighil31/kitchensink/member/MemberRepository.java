package io.github.mighil31.kitchensink.member;

import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Replaces legacy's {@code data/MemberRepository} (EntityManager + Criteria queries). */
public interface MemberRepository extends MongoRepository<Member, ObjectId> {

    /**
     * Legacy's {@code findAllOrderedByName}. No collation, so the order is binary and
     * case-sensitive ({@code Alice < Bob < alice}), as on legacy (ADR 0009); served by the
     * {@code name_asc} index.
     */
    List<Member> findAllByOrderByNameAsc();
}
