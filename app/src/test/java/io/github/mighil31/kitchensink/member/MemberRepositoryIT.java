package io.github.mighil31.kitchensink.member;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.mighil31.kitchensink.TestcontainersConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Full context (same as the other ITs, so the cached context and container are shared). */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MemberRepositoryIT {

    @Autowired
    MemberRepository repository;

    @BeforeEach
    void empty() {
        repository.deleteAll();
    }

    @Test
    void listIsInLegacyBinaryOrder() {
        for (String name : List.of("zed", "Émile", "bob", "Alice", "alice", "Bob")) {
            repository.insert(Member.of(name, UUID.randomUUID() + "@example.com", "2125551234"));
        }

        assertThat(repository.findAllByOrderByNameAsc())
                .extracting(Member::name)
                .containsExactly("Alice", "Bob", "alice", "bob", "zed", "Émile");
    }

    @Test
    void findsByIdAfterInsert() {
        Member saved = repository.insert(Member.of("Jane Doe", "jane@example.com", "2125551234"));

        assertThat(repository.findById(saved.id())).contains(saved);
    }
}
