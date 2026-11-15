package io.github.mighil31.kitchensink.member;

import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

/** Replaces legacy's {@code data/MemberRepository} (EntityManager + Criteria queries). */
public interface MemberRepository extends MongoRepository<Member, ObjectId> {

    /**
     * Legacy's {@code findAllOrderedByName}. No collation, so the order is binary and
     * case-sensitive ({@code Alice < Bob < alice}), as on legacy (ADR 0009). Members with the same
     * name are ordered by id, so the order is stable. Served by the {@code name_id_asc} index.
     */
    List<Member> findAllByOrderByNameAscIdAsc();

    /**
     * One page in the same order (gap A3). A {@link Slice} fetches one extra document to know
     * whether a next page exists, instead of counting the whole collection.
     */
    Slice<Member> findAllByOrderByNameAscIdAsc(Pageable page);
}
