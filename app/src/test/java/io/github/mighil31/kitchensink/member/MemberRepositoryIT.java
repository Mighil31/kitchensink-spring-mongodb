package io.github.mighil31.kitchensink.member;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mighil31.kitchensink.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Full context (same as the other ITs, so the cached context and container are shared). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MemberRepositoryIT {

    @Autowired
    MemberRepository repository;

    @Autowired
    MongoTemplate mongoTemplate;

    @BeforeEach
    void empty() {
        repository.deleteAll();
    }

    @Test
    void listIsInLegacyBinaryOrder() {
        for (String name : List.of("zed", "Émile", "bob", "Alice", "alice", "Bob")) {
            repository.insert(Member.of(name, UUID.randomUUID() + "@example.com", "2125551234"));
        }

        assertThat(repository.findAllByOrderByNameAscIdAsc())
                .extracting(Member::name)
                .containsExactly("Alice", "Bob", "alice", "bob", "zed", "Émile");
    }

    /**
     * Gap A3: paging must neither repeat nor skip members, even when names are equal; the id
     * breaks the ties. Walking every page gives exactly the full list.
     */
    @Test
    void pagesCoverTheListExactlyOnceWithEqualNames() {
        for (String name : List.of("Sam", "Sam", "Sam", "Ann", "Sam", "Zoe", "Sam")) {
            repository.insert(Member.of(name, UUID.randomUUID() + "@example.com", "2125551234"));
        }
        List<Member> all = repository.findAllByOrderByNameAscIdAsc();

        List<Member> paged = new ArrayList<>();
        Slice<Member> page = repository.findAllByOrderByNameAscIdAsc(PageRequest.of(0, 2));
        paged.addAll(page.getContent());
        while (page.hasNext()) {
            page = repository.findAllByOrderByNameAscIdAsc(page.nextPageable());
            paged.addAll(page.getContent());
        }

        assertThat(paged).containsExactlyElementsOf(all).doesNotHaveDuplicates();
    }

    /** The sort is served by the name_id_asc index, not done in memory. */
    @Test
    void listSortUsesTheNameIdIndex() {
        Document plan = mongoTemplate
                .getCollection(Member.COLLECTION)
                .find()
                .sort(new Document("name", 1).append("_id", 1))
                .limit(2)
                .explain();

        assertThat(plan.toJson()).contains("\"indexName\": \"name_id_asc\"").doesNotContain("\"stage\": \"SORT\"");
    }

    @Test
    void findsByIdAfterInsert() {
        Member saved = repository.insert(Member.of("Jane Doe", "jane@example.com", "2125551234"));

        assertThat(repository.findById(saved.id())).contains(saved);
    }
}
