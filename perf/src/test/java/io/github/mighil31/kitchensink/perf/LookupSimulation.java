package io.github.mighil31.kitchensink.perf;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.util.List;

/** Reads only: GET /members/{id} for members seeded before the measured window. */
public class LookupSimulation extends Simulation {

    private static final int SEEDED = 200;
    private volatile List<String> ids;

    ScenarioBuilder lookup = scenario("Lookup").exec(Requests.lookup(() -> ids));

    {
        setUp(lookup.injectOpen(constantUsersPerSec(PerfConfig.rate()).during(PerfConfig.duration())))
                .protocols(PerfConfig.httpProtocol());
    }

    @Override
    public void before() {
        ids = SeedMembers.create(SEEDED);
    }
}
