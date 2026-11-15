package io.github.mighil31.kitchensink.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.mighil31.kitchensink.TestcontainersConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MemberRegistrationIT {

    @Autowired
    MemberRegistration registration;

    @Autowired
    MemberRepository repository;

    @BeforeEach
    void empty() {
        repository.deleteAll();
    }

    @Test
    void registersMember() {
        Member saved = registration.register(new MemberRequest("Jane Doe", "jane@example.com", "2125551234"));

        assertThat(repository.findById(saved.id()))
                .contains(new Member(saved.id(), "Jane Doe", "jane@example.com", "2125551234", null));
    }

    @Test
    void duplicateEmailIsRejectedAndNotStored() {
        registration.register(new MemberRequest("Jane Doe", "jane@example.com", "2125551234"));

        assertThatThrownBy(() -> registration.register(new MemberRequest("Other", "jane@example.com", "2125550000")))
                .isInstanceOf(EmailAlreadyRegisteredException.class);
        assertThat(repository.count()).isEqualTo(1);
    }

    /** Gap B1: legacy's check-then-insert let concurrent registrations both succeed. */
    @Test
    void concurrentRegistrationsWithSameEmailStoreExactlyOne() throws Exception {
        int threads = 16;
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            try {
                registration.register(new MemberRequest("Racer", "race@example.com", "2125551234"));
                return true;
            } catch (EmailAlreadyRegisteredException e) {
                return false;
            }
        };

        List<Future<Boolean>> results = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            for (int i = 0; i < threads; i++) {
                results.add(pool.submit(attempt));
            }
            start.countDown();
        }

        long succeeded = 0;
        for (Future<Boolean> result : results) {
            if (result.get()) {
                succeeded++;
            }
        }
        assertThat(succeeded).isEqualTo(1);
        assertThat(repository.count()).isEqualTo(1);
    }
}
