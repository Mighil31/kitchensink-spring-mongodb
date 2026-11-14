package io.github.mighil31.kitchensink.perf;

import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.scenario;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;

/** Writes only: POST /members at a constant arrival rate, every request a new member. */
public class RegistrationSimulation extends Simulation {

    ScenarioBuilder register = scenario("Register").exec(Requests.register());

    {
        setUp(register.injectOpen(constantUsersPerSec(PerfConfig.rate()).during(PerfConfig.duration())))
                .protocols(PerfConfig.httpProtocol());
    }
}
