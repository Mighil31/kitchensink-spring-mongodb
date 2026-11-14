package io.github.mighil31.kitchensink.perf;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.percent;
import static io.gatling.javaapi.core.CoreDsl.randomSwitch;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import java.util.List;

/**
 * Realistic traffic: 80% lookups, 20% registrations. The SLO is judged on this simulation,
 * and the knee is found by running it at increasing rates.
 */
public class MixedSloSimulation extends Simulation {

    private static final int SEEDED = 200;
    private volatile List<String> ids;

    ScenarioBuilder mixed = scenario("Mixed")
            .exec(randomSwitch()
                    .on(
                            percent(80.0).then(Requests.lookup(() -> ids)),
                            percent(20.0).then(Requests.register())));

    {
        setUp(mixed.injectOpen(constantUsersPerSec(PerfConfig.rate()).during(PerfConfig.duration())))
                .protocols(PerfConfig.httpProtocol());
    }

    @Override
    public void before() {
        ids = SeedMembers.create(SEEDED);
    }
}
