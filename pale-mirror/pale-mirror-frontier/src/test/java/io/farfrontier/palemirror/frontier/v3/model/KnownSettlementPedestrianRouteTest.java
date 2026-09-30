package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovement;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementColdAdvanced;
import io.farfrontier.palemirror.frontier.v3.model.navigation.ActorMovementContext;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.process.ActorMovementProcess;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnownSettlementPedestrianRouteTest {
    @Test void crowdCannotTurnAServiceExitIntoPermanentKnownTerrain() {
        FrontierWorldState initial = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:known-pedestrian-crowd"), 20260918065L));
        Settlement settlement = initial.bootstrap().settlements().get(10);
        SubjectId actorId = settlement.residents().getFirst().id();
        SurfaceAnchor home = initial.actorLocations().get(actorId).supportingSurface();
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        FrontierWorldState atService = initial.withActorBody(actorId, port.serviceSurface().standingBody());
        MovementOrder order = new MovementOrder(actorId, actorId, 0L, 1L, List.of(home),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        SubjectId depotId = FrontierWorldState.depotId(settlement.id());
        List<SurfaceAnchor> baseline = KnownServiceExitNavigation.path(atService, settlement.id(), depotId, order);
        assertTrue(baseline.size() > 2);

        var actors = new LinkedHashMap<>(atService.actorLocations());
        int index = 1;
        for (Resident resident : settlement.residents()) {
            if (resident.id().equals(actorId)) continue;
            actors.put(resident.id(), ActorLocation.standingOn(baseline.get(index)));
            index = index + 1 < baseline.size() - 1 ? index + 1 : 1;
        }
        FrontierWorldState crowded = atService.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        assertEquals(baseline, KnownServiceExitNavigation.path(crowded, settlement.id(), depotId, order));
        assertEquals(baseline, KnownSettlementPedestrianRoute.path(crowded, settlement.id(),
                port.serviceSurface(), order, List.of(new KnownSettlementPedestrianRoute.Passage(depot.id(),
                        KnownSettlementPedestrianRoute.Passage.Kind.DEPOT_ACCESS))));
        ActorMovement movement = new ActorMovement(order, 27_000L,
                new ActorMovementContext.ServiceExit(settlement.id(), depotId));
        FrontierWorldState moving = crowded.withChanges(FrontierWorldStateUpdate.begin()
                .actorMovements(Map.of(actorId, movement)));
        var action = ActorMovementProcess.progress(movement, 27_001L);
        var started = ActorMovementProcess.plan(moving, action, 27_001L);
        ActorMovementColdAdvanced event = assertInstanceOf(ActorMovementColdAdvanced.class,
                started.getFirst().payload());
        FrontierWorldState travelling = ActorMovementProcess.reduceColdAdvanced(moving, actorId, event);
        List<SurfaceAnchor> firstSegment = travelling.actorMovements().get(actorId)
                .coldTravel().orElseThrow().route();
        assertEquals(baseline.subList(0, firstSegment.size()), firstSegment);
        assertTrue(port.accessBoundary().cleared(firstSegment.getLast().standingBody()));

        FrontierWorldState physicallyBlocked = crowded.recordPhysicalDelta(new PhysicalDelta(
                home.support(), PhysicalDeltaKind.UNKNOWN_SCAR, Optional.empty(), Optional.empty(),
                "test:known-pedestrian-hard-block"));
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                () -> KnownServiceExitNavigation.path(physicallyBlocked, settlement.id(), depotId, order));
    }

    @Test void facilityPassageMustNameTheDeclaredKindAndLocalOwner() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:known-pedestrian-passage"), 421L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SubjectId resident = settlement.residents().getFirst().id();
        SurfaceAnchor body = state.actorLocations().get(resident).supportingSurface();
        MovementOrder order = new MovementOrder(resident, resident, 0L, 1L, List.of(body),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        assertThrows(IllegalArgumentException.class, () -> KnownSettlementPedestrianRoute.path(state,
                settlement.id(), body, order, List.of(new KnownSettlementPedestrianRoute.Passage(depot.id(),
                        KnownSettlementPedestrianRoute.Passage.Kind.WORKSHOP_EXTERIOR))));
    }
}
