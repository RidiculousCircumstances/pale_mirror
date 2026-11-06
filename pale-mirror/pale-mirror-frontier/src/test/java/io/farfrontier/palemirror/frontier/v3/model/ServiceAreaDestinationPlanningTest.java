package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class ServiceAreaDestinationPlanningTest {
    @Test void occupiedServiceCanClearWithoutAcquiringAnotherTurnOrReentering() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:private-clearance"), 421L));
        var settlement = state.bootstrap().settlements().getFirst();
        var actor = settlement.residents().getFirst().id();
        var structure = settlement.structures().stream().filter(s -> s.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        var depotId = FrontierWorldState.depotId(settlement.id());
        var point=ServiceBoundaryComposition.declaration(state,depotId).identity();
        var boundary = ServiceAccessCoordinator.boundary(state,depotId);
        var start = state.inventory().containers().get(depotId).storage().orElseThrow().endpoint().station();
        state = state.withActorBody(actor, start.standingBody());
        var beforeInventory = state.inventory();
        var beforeAccess = ServiceAccessCapabilities.current(state, depotId);
        var clearance = ServiceClearanceRoutes.fromService(state, actor, start,point);
        assertFalse(clearance.publicDestination(start));
        assertTrue(boundary.occupied(start.standingBody()));
        var probes = new AtomicInteger();
        var target = ServiceAreaDestinations.select(clearance, actor, start, Set.of(), p -> true,
                p -> { probes.incrementAndGet(); return true; }).orElseThrow();
        var route = clearance.knowledge().plannedBetween(start, target);
        assertTrue(boundary.allowsWaitingRoute(route), "an incumbent may clear its held boundary without another entrance turn");
        assertTrue(clearance.allows(route));
        assertEquals(1, probes.get(), "route permission is resolved before spending a native feasibility probe");
        assertFalse(clearance.temporary(target));
        assertTrue(clearance.evaluatedPointCount() < ServiceBoundaryComposition.operationalDeclarations(state).size(),
                "one local departure must not instantiate every settlement's service geometry");
        assertSame(beforeInventory, state.inventory()); assertEquals(beforeAccess, ServiceAccessCapabilities.current(state, depotId));
        assertFalse(clearance.allows(List.of(start, start)), "clearing cannot end at service");
        var reentry = new java.util.ArrayList<>(route);
        for (int i = route.size() - 2; i >= 0; i--) reentry.add(route.get(i));
        reentry.addAll(route.subList(1, route.size()));
        assertFalse(clearance.allows(reentry), "a completed outward crossing grants no later re-entry");
        var outsider = ServiceClearanceRoutes.fromService(state, actor, target,point);
        var intrusion = new java.util.ArrayList<>(route.reversed()); intrusion.addAll(route.subList(1, route.size()));
        assertFalse(outsider.allows(intrusion), "an outside actor cannot claim another actor's private escape");
        var damaged = state.recordPhysicalDelta(new PhysicalDelta(start.support(), PhysicalDeltaKind.UNKNOWN_SCAR,
                java.util.Optional.empty(), java.util.Optional.empty(), "test:lost-threshold"));
        assertFalse(ServiceClearanceRoutes.fromService(damaged, actor, start,point).allows(route), "departure permission never invents missing support");
    }
    @Test void unusedNeighbouringInventoryAllowsTransitButNeverAnUnprovenPhysicalRoute() {
        var initial=FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:storage-aisle"),421));
        var home=initial.bootstrap().settlements().getFirst().id();var state=initial;
        for(int x:List.of(0,5)) {
            var id=new io.farfrontier.palemirror.frontier.v3.api.SubjectId("container:aisle-"+x);
            var endpoint=new ShipmentEndpoint.Storage(home,id,SurfaceAnchor.at(x+1,63,1),java.util.Optional.empty(),SurfaceAnchor.at(x+1,63,1));
            var record=ContainerRecord.storage(id,home,27,StorageBlockKind.CHEST,
                    new ResourceStoragePolicy(endpoint,ResourceStorageFilter.all(),0,ResourceStorageRole.WAREHOUSE));
            state=new ResourceStorageDeclared(record,new BlockPosition(x,64,0)).apply(state,home);
        }
        var actor=state.bootstrap().settlements().getFirst().residents().getFirst().id();
        var route=java.util.stream.IntStream.rangeClosed(1,10).mapToObj(x->SurfaceAnchor.at(x,63,1)).toList();
        var clearance=ServiceClearanceRoutes.from(state,actor,route.getFirst());
        assertTrue(clearance.allows(route),"an unused neighbouring interaction envelope is not a transit wall");
        assertTrue(ServiceAccessCapabilities.current(state,new io.farfrontier.palemirror.frontier.v3.api.SubjectId("container:aisle-5")).isEmpty());
        var damaged=state.recordPhysicalDelta(new PhysicalDelta(route.get(4).support().offset(0,1,0),PhysicalDeltaKind.UNKNOWN_SCAR,
                java.util.Optional.empty(),java.util.Optional.empty(),"test:aisle-obstruction"));
        assertFalse(ServiceClearanceRoutes.from(damaged,actor,route.getFirst()).allows(route),"shared transit retains real geometry checks");
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
                    () -> ServiceAreaDestinations.select(ServiceClearanceRoutes.from(state,actor,start),actor,start,Set.of(),
                            candidate -> Math.abs(candidate.x()-start.x())+Math.abs(candidate.z()-start.z())>4,candidate -> true)));
            assertEquals(PedestrianRouteResult.Status.PLANNING, observed.result().status());
            assertEquals(1, queries.get(), "an unfinished candidate must not fan out through the whole resting area");
            assertEquals(1, observed.requests().size());
            assertEquals(start, state.actorLocations().get(actor).supportingSurface());
        }
        var selected = ServiceAreaDestinations.select(ServiceClearanceRoutes.from(state,actor,start),actor,start,Set.of(start),candidate -> true,candidate -> true).orElseThrow();
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
            var selected = ServiceAreaDestinations.select(ServiceClearanceRoutes.from(state,actor,start),actor,start,Set.of(),candidate -> true,candidate -> true).orElseThrow();
            knowledge.requireRoute(knowledge.localMovementRoutes(start).get(selected));
            assertEquals(0, queries.get(), "a known local sidestep must not depend on the global planner queue");
            assertTrue(ServiceAreaDestinations.select(ServiceClearanceRoutes.from(state,actor,start),actor,start,Set.of(),candidate -> false,candidate -> true).isEmpty());
            assertEquals(0, queries.get(), "a place rejected by the physical/passage policy must not start a route");
        }
    }
}
