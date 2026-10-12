package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ServiceAreaDestinationPlanningTest {
    @Test void unfinishedDestinationSearchIsOneCooperativeWaitNotAnAbsentOrInventedRestingPlace() throws Exception {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:parking-planning"), 421L));
        var actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var start = state.actorLocations().get(actor).supportingSurface();
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        var queries = new AtomicInteger();
        try (var binding = PedestrianRoutePlanning.bind((g, from, to) -> {
            queries.incrementAndGet();
            return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 1, 1, "pending");
        })) {
            var observed = PedestrianRoutePlanning.observe(() -> assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                    () -> ServiceAreaDestinations.select(List.of(), actor, start, knowledge, Set.of())));
            assertEquals(PedestrianRouteResult.Status.PLANNING, observed.result().status());
            assertEquals(1, queries.get(), "an unfinished candidate must not fan out through the whole resting area");
            assertEquals(1, observed.requests().size());
            assertEquals(start, state.actorLocations().get(actor).supportingSurface());
        }
        var selected = ServiceAreaDestinations.select(List.of(), actor, start, knowledge, Set.of(start)).orElseThrow();
        assertTrue(ServiceAreaDestinations.hasStandingClearance(selected, Set.of(start)));
        knowledge.requireRoute(knowledge.plannedBetween(start, selected));
    }
}
