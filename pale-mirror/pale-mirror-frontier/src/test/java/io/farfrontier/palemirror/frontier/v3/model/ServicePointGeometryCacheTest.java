package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServicePointGeometryCacheTest {
    @Test void remoteChangeReusesServiceGeometryButLocalObstructionRemovesAnExit() {
        var state = FrontierWorldState.initial(FrontierBootstrapper.create(new WorldId("frontier:local-service"), 41));
        var settlement = state.bootstrap().settlements().getFirst();
        var depot = settlement.structures().stream().filter(s -> s.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        var point = SettlementServiceAccessPoints.forDepot(state, settlement, depot);
        var cache = new ServicePointGeometryCache();
        var request = new ServicePointGeometryCache.Request(depot.id(), settlement.id(), point.station(), point.boundary(),
                point.station(), List.of(), settlement.residents().size(), true);
        var passages = List.of(new KnownPedestrianRouteKnowledge.Passage(depot, KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS));
        var before = cache.get(depot.id(), request, KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages));
        assertFalse(before.egressSurfaces().isEmpty());
        var far = point.station().support().offset(160, 1, 160);
        state = new NavigationGeometryObserved(state.navigationGeometry().revision(), Map.of(far, NavigationObservation.known(NavigationCell.SOLID)))
                .apply(state, NavigationGeometryObserved.OWNER);
        var unchanged = cache.get(depot.id(), request, KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages));
        assertSame(before, unchanged, "unrelated region changes must perform zero service geometry rebuilds");
        var exit = before.egressSurfaces().iterator().next();
        state = new NavigationGeometryObserved(state.navigationGeometry().revision(), Map.of(exit.support().offset(0, 1, 0), NavigationObservation.known(NavigationCell.SOLID)))
                .apply(state, NavigationGeometryObserved.OWNER);
        var obstructed = cache.get(depot.id(), request, KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(), passages));
        assertNotSame(before, obstructed);
        assertFalse(obstructed.egressSurfaces().contains(exit), "a local new wall cannot inherit a cached escape cell");
        assertTrue(before.egressSurfaces().contains(exit), "immutable prior knowledge must remain unchanged");
    }
}
