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
                    () -> ServiceAreaDestinations.select(List.of(), actor, start, knowledge, Set.of(),
                            candidate -> Math.abs(candidate.x() - start.x()) + Math.abs(candidate.z() - start.z()) > 4)));
            assertEquals(PedestrianRouteResult.Status.PLANNING, observed.result().status());
            assertEquals(1, queries.get(), "an unfinished candidate must not fan out through the whole resting area");
            assertEquals(1, observed.requests().size());
            assertEquals(start, state.actorLocations().get(actor).supportingSurface());
        }
        var selected = ServiceAreaDestinations.select(List.of(), actor, start, knowledge, Set.of(start)).orElseThrow();
        assertTrue(ServiceAreaDestinations.hasStandingClearance(selected, Set.of(start)));
        knowledge.requireRoute(knowledge.plannedBetween(start, selected));
    }
    @Test void localEscapeDoesNotQueueGlobalPlanningAndRejectedPlacesNeverQueueARoute() throws Exception {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:local-escape"), 421L));
        var actor = state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var start = state.actorLocations().get(actor).supportingSurface();
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state);
        var queries = new AtomicInteger();
        try (var binding = PedestrianRoutePlanning.bind((g, from, to) -> {
            queries.incrementAndGet();
            return new PedestrianRouteResult(PedestrianRouteResult.Status.PLANNING, List.of(), 1, 1, "pending");
        })) {
            var selected = ServiceAreaDestinations.select(List.of(), actor, start, knowledge, Set.of()).orElseThrow();
            knowledge.requireRoute(knowledge.localMovementRoutes(start).get(selected));
            assertEquals(0, queries.get(), "a known local sidestep must not depend on the global planner queue");
            assertTrue(ServiceAreaDestinations.select(List.of(), actor, start, knowledge, Set.of(), candidate -> false).isEmpty());
            assertEquals(0, queries.get(), "a place rejected by the physical/passage policy must not start a route");
        }
    }
}
