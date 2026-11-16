package io.github.mighil31.kitchensink.member;

import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MemberRepository extends MongoRepository<Member, ObjectId> {

    /**
     * Binary, case-sensitive order ({@code Alice < Bob < alice}), as on legacy (ADR 0009); the id
     * breaks ties so paging is stable. Served by the {@code name_id_asc} index.
     */
    List<Member> findAllByOrderByNameAscIdAsc();

    /** A {@link Slice}, not a Page: it fetches one extra document instead of counting the collection. */
    Slice<Member> findAllByOrderByNameAscIdAsc(Pageable page);
}
