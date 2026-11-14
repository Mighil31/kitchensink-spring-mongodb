package io.github.mighil31.kitchensink.perf;

import io.gatling.javaapi.core.ChainBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.feed;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

/** The two operations every simulation is built from. */
public final class Requests {

    private Requests() {}

    /** POST a new, unique member. 200 on legacy, 201 on the new app. */
    public static ChainBuilder register() {
        return feed(Members.uniqueBodies())
                .exec(http("POST /members")
                        .post("/members")
                        .body(StringBody("#{memberBody}"))
                        .check(status().in(200, 201)));
    }

    /** GET a random member from {@code ids} (seeded before the run). */
    public static ChainBuilder lookup(Supplier<List<String>> ids) {
        return feed(Stream.generate(() -> {
                    List<String> all = ids.get();
                    return Map.<String, Object>of("memberId", all.get(ThreadLocalRandom.current().nextInt(all.size())));
                }).iterator())
                .exec(http("GET /members/{id}")
                        .get("/members/#{memberId}")
                        .check(status().is(200)));
    }
}
