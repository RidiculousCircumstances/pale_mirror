package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ServiceAreaDestinationPlanningTest {
    @Test void privateThresholdCanClearThroughServiceWithoutAcquiringItsTurnOrReentering() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:private-clearance"), 421L));
        var settlement = state.bootstrap().settlements().getFirst();
        var actor = settlement.residents().getFirst().id();
        var structure = settlement.structures().stream().filter(s -> s.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        var port = SettlementDepotServicePort.forDepot(structure);
        var start = port.thresholdSurface();
        state = state.withActorBody(actor, start.standingBody());
        var beforeInventory = state.inventory();
        var depotId = FrontierWorldState.depotId(settlement.id());
        var beforeAccess = ServiceAccessCapabilities.current(state, depotId);
        var clearance = ServiceClearanceRoutes.from(state, actor, start);
        assertFalse(clearance.publicDestination(start));
        assertFalse(port.accessBoundary().occupied(start.standingBody()));
        var probes = new AtomicInteger();
        var target = ServiceAreaDestinations.select(clearance, actor, start, Set.of(), p -> true,
                p -> { probes.incrementAndGet(); return true; }).orElseThrow();
        var route = clearance.knowledge().plannedBetween(start, target);
        assertFalse(port.accessBoundary().allowsWaitingRoute(route), "old generic entry prohibition traps the inner-threshold occupant");
        assertTrue(clearance.allows(route));
        assertEquals(1, probes.get(), "route permission is resolved before spending a native feasibility probe");
        assertTrue(clearance.points().stream().noneMatch(p -> ServiceAreaDestinations.temporary(p, target)));
        assertSame(beforeInventory, state.inventory()); assertEquals(beforeAccess, ServiceAccessCapabilities.current(state, depotId));
        assertFalse(clearance.allows(List.of(start, port.serviceSurface())), "clearing cannot end at service");
        var reentry = new java.util.ArrayList<>(route);
        for (int i = route.size() - 2; i >= 1; i--) reentry.add(route.get(i));
        reentry.addAll(route.subList(2, route.size()));
        assertFalse(clearance.allows(reentry), "a completed outward crossing grants no later re-entry");
        var outsider = ServiceClearanceRoutes.from(state, actor, target);
        var intrusion = new java.util.ArrayList<>(route.reversed()); intrusion.addAll(route.subList(1, route.size()));
        assertFalse(outsider.allows(intrusion), "an outside actor cannot claim another actor's private escape");
        var damaged = state.recordPhysicalDelta(new PhysicalDelta(start.support(), PhysicalDeltaKind.UNKNOWN_SCAR,
                java.util.Optional.empty(), java.util.Optional.empty(), "test:lost-threshold"));
        assertFalse(ServiceClearanceRoutes.from(damaged, actor, start).allows(route), "departure permission never invents missing support");
    }
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
