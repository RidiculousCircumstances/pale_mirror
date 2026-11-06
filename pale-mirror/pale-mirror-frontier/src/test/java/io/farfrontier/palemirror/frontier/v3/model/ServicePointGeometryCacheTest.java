package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServicePointGeometryCacheTest {
    @Test void storageBufferUsesTheDeclaredExteriorAndOnlyItsConnectedSurface() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:indoor-storage-buffer"),139));
        var home = state.bootstrap().settlements().getFirst();
        var id = CommunalDiningPlan.initialFoodStorage(state.bootstrap(),home.id());
        var declaration = ServiceBoundaryComposition.declaration(state,id);
        var point = ServiceBoundaryComposition.geometry(state,declaration.identity());
        var endpoint = (ShipmentEndpoint.Storage) ResourceStorageAccess.at(state,id).endpoint();
        var knowledge = KnownPedestrianRouteKnowledge.forPublicJourney(state,home.id(),List.of());
        var connected = io.farfrontier.palemirror.frontier.v3.model.geometry.ReachableSurfaceArea.select(
                knowledge.geometry(),List.of(endpoint.exteriorApproach()),endpoint.exteriorApproach(),
                ServiceWaitingTargets.radius(home.residents().size()),4096,2048,p -> true).selected();
        assertFalse(point.waitingSurfaces().isEmpty(),"the ordinary kitchen retains an accessible outdoor buffer");
        assertTrue(connected.containsAll(point.waitingSurfaces()),"isolated upper surfaces cannot become waiting destinations");
        for(var candidate:point.waitingSurfaces()) {
            assertTrue(Math.abs(candidate.x()-endpoint.exteriorApproach().x()) <= ServiceWaitingTargets.radius(home.residents().size()));
            assertTrue(Math.abs(candidate.z()-endpoint.exteriorApproach().z()) <= ServiceWaitingTargets.radius(home.residents().size()));
            assertTrue(point.boundary().cleared(candidate.standingBody()));
            assertTrue(knowledge.clearStation(candidate),"waiting cannot require private service access");
            assertFalse(FacilityPedestrianContacts.of(state.bootstrap()).noParking().contains(candidate.support()),
                    "declared circulation and interior work floors are never waiting parking");
        }
    }

    @Test void remoteChangeReusesServiceGeometryButLocalObstructionRemovesAnExit() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:local-service"), 41));
        var settlement = state.bootstrap().settlements().getFirst();
        var depot = settlement.structures().stream().filter(s -> s.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        var point = SettlementServiceAccessPoints.forDepot(state, settlement, depot);
        var cache = new ServicePointGeometryCache();
        var request = new ServicePointGeometryCache.Request(depot.id(), settlement.id(), point.station(), point.boundary(),
                point.station(), List.of(), settlement.residents().size(), true);
        var passages = List.of(new KnownPedestrianRouteKnowledge.Passage(depot, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS));
        var before = cache.get(depot.id(), request, KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages),
                KnownPedestrianRouteKnowledge.forPublicJourney(state,settlement.id(),List.of()),
                FacilityPedestrianContacts.of(state.bootstrap()).noParking());
        assertFalse(before.egressSurfaces().isEmpty());
        var far = point.station().support().offset(160, 1, 160);
        state = new NavigationGeometryObserved(state.navigationGeometry().revision(), Map.of(far, NavigationObservation.known(NavigationCell.SOLID)))
                .apply(state, NavigationGeometryObserved.OWNER);
        var unchanged = cache.get(depot.id(), request, KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages),
                KnownPedestrianRouteKnowledge.forPublicJourney(state,settlement.id(),List.of()),
                FacilityPedestrianContacts.of(state.bootstrap()).noParking());
        assertSame(before, unchanged, "unrelated region changes must perform zero service geometry rebuilds");
        var exit = before.egressSurfaces().iterator().next();
        state = new NavigationGeometryObserved(state.navigationGeometry().revision(), Map.of(exit.support().offset(0, 1, 0), NavigationObservation.known(NavigationCell.SOLID)))
                .apply(state, NavigationGeometryObserved.OWNER);
        var obstructed = cache.get(depot.id(), request, KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages),
                KnownPedestrianRouteKnowledge.forPublicJourney(state,settlement.id(),List.of()),
                FacilityPedestrianContacts.of(state.bootstrap()).noParking());
        assertNotSame(before, obstructed);
        assertFalse(obstructed.egressSurfaces().contains(exit), "a local new wall cannot inherit a cached escape cell");
        assertTrue(before.egressSurfaces().contains(exit), "immutable prior knowledge must remain unchanged");
    }
}
