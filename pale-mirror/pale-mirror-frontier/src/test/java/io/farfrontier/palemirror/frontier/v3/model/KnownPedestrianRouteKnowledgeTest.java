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

class KnownPedestrianRouteKnowledgeTest {
    @Test void mutableRouteOverlaysCannotPoisonCachedBootstrapOccupancy() {
        var bootstrap = FrontierBootstrapper.create(new WorldId("frontier:occupancy-cache"), 421L);
        var original = KnownPedestrianRouteKnowledge.staticOccupancy(bootstrap);
        assertTrue(!original.isEmpty());
        var changed = KnownPedestrianRouteKnowledge.staticOccupancy(bootstrap);
        changed.clear();
        assertEquals(original, KnownPedestrianRouteKnowledge.staticOccupancy(bootstrap));
        var other = FrontierBootstrapper.create(new WorldId("frontier:other-occupancy-cache"), 422L);
        KnownPedestrianRouteKnowledge.staticOccupancy(other).clear();
        assertEquals(original, KnownPedestrianRouteKnowledge.staticOccupancy(bootstrap));
    }
    @Test void clearwaterRoadHintsUseTheProjectedTopRatherThanTerrainUnderTheRoad() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:clearwater-road-support"), 20260918065L));
        var knowledge = KnownPedestrianRouteKnowledge.forSettlement(state, new SubjectId("settlement:7"), List.of());
        org.junit.jupiter.api.Assertions.assertSame(knowledge,
                KnownPedestrianRouteKnowledge.forSettlement(state.withActorBody(new SubjectId("resident:7-1"),
                        state.actorLocations().get(new SubjectId("resident:7-1")).body()),
                        new SubjectId("settlement:7"), List.of()),
                "actor-only changes must not recompile immutable ground/obstacles");
        BlockPosition road = new BlockPosition(115, 64, 14);
        assertTrue(FrontierRouteNetwork.footprint(state.bootstrap(), state.routeTopology()).surfaceCells().contains(road));
        assertEquals(new SurfaceAnchor(road), knowledge.supportAt(road.x(), road.z()));
        var recovered = new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().decode(
                new io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec().encode(state));
        assertEquals(knowledge.supportAt(115, 14),
                KnownPedestrianRouteKnowledge.forSettlement(recovered, new SubjectId("settlement:7"), List.of())
                        .supportAt(115, 14));
    }
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
            actors.put(resident.id(), ActorLocation.standingOn(baseline.get(index), ActorKind.RESIDENT));
            index = index + 1 < baseline.size() - 1 ? index + 1 : 1;
        }
        FrontierWorldState crowded = atService.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors));
        assertEquals(baseline, KnownServiceExitNavigation.path(crowded, settlement.id(), depotId, order));
        assertEquals(baseline, KnownPedestrianRouteKnowledge.path(crowded, settlement.id(),
                port.serviceSurface(), order, List.of(new KnownPedestrianRouteKnowledge.Passage(depot,
                        KnownPedestrianRouteKnowledge.Passage.Reach.PUBLIC_ACCESS))));
        ActorMovement movement = new ActorMovement(order, 27_000L,
                new ActorMovementContext.ServiceExit(settlement.id(), depotId),
                crowded.actorExecutions().next(actorId, io.farfrontier.palemirror.frontier.v3.model.execution.ActorActivityKind.SERVICE_EXIT, actorId));
        FrontierWorldState moving = crowded.withChanges(FrontierWorldStateUpdate.begin()
                .actorMovements(Map.of(actorId, movement))
                .actorExecutions(crowded.actorExecutions().begin(movement.executionId(), 0L)));
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

    @Test void facilityPassageMustNameTheExactDeclaredFacility() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:known-pedestrian-passage"), 421L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SubjectId resident = settlement.residents().getFirst().id();
        SurfaceAnchor body = state.actorLocations().get(resident).supportingSurface();
        MovementOrder order = new MovementOrder(resident, resident, 0L, 1L, List.of(body),
                TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
        SettlementStructure forged = new SettlementStructure(depot.id(), depot.settlementId(),
                StructureKind.WORKSHOP, depot.anchor(), depot.facing());
        assertThrows(IllegalArgumentException.class, () -> KnownPedestrianRouteKnowledge.path(state,
                settlement.id(), body, order, List.of(new KnownPedestrianRouteKnowledge.Passage(forged,
                        KnownPedestrianRouteKnowledge.Passage.Reach.EXTERIOR))));
    }

    @Test void existingFacilityPortsShareTheSamePassageContract() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:known-pedestrian-port-reuse"), 421L));
        Settlement settlement = state.bootstrap().settlements().getFirst();
        SubjectId resident = settlement.residents().getFirst().id();
        for (StructureKind kind : List.of(StructureKind.HALL, StructureKind.DEPOT,
                StructureKind.WORKSHOP, StructureKind.INFIRMARY)) {
            SettlementStructure facility = settlement.structures().stream()
                    .filter(structure -> structure.kind() == kind).findFirst().orElseThrow();
            var route = KnownPedestrianRouteKnowledge.forSettlement(state, settlement.id(),
                    List.of(new KnownPedestrianRouteKnowledge.Passage(facility,
                            KnownPedestrianRouteKnowledge.Passage.Reach.EXTERIOR)));
            SurfaceAnchor exterior = FrontierTraversalPlan.facilityPort(facility).orElseThrow()
                    .exteriorApproach().getFirst();
            assertEquals(SettlementPedestrianGround.surveyedSupport(state.bootstrap(),
                    SettlementPedestrianGround.localSupports(state.bootstrap(), settlement.id()),
                    exterior.x(), exterior.z()), route.supportAt(exterior.x(), exterior.z()));
            MovementOrder order = new MovementOrder(resident, resident, 0L, 1L, List.of(exterior),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            assertEquals(List.of(exterior), route.path(exterior, order));
        }
    }

    @Test void fieldOverlayUsesTheSameChangedServiceCellBarrier() {
        FrontierWorldState state = FrontierWorldState.initial(FrontierBootstrapper.create(
                new WorldId("frontier:known-pedestrian-field-overlay"), 421L));
        ResourceSite site = state.resourceSite(new SubjectId("site:1-wheat-field"));
        Settlement settlement = state.bootstrap().settlements().stream()
                .filter(candidate -> candidate.id().equals(site.settlementId())).findFirst().orElseThrow();
        SettlementStructure depot = settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.DEPOT).findFirst().orElseThrow();
        SettlementDepotServicePort port = SettlementDepotServicePort.forDepot(depot);
        SurfaceAnchor start = port.exteriorApproach();
        SubjectId resident = settlement.residents().getFirst().id();
        MovementOrder order = new MovementOrder(resident, resident, 0L, 1L,
                List.of(port.serviceSurface()), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
        ResourceFieldCycle cycle = state.resourceSites().cycle(site.id());
        ResourceFieldCycle foreign = state.resourceSites().cycles().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(site.id())).findFirst().orElseThrow().getValue();
        assertThrows(IllegalArgumentException.class,
                () -> KnownPedestrianRouteKnowledge.forField(state, site, foreign, port, start));
        assertEquals(List.of(start, port.serviceSurface()),
                KnownPedestrianRouteKnowledge.forField(state, site, cycle, port, start).path(start, order));
        FrontierWorldState changed = state.recordPhysicalDelta(new PhysicalDelta(
                port.serviceSurface().support().offset(0, 1, 0), PhysicalDeltaKind.UNKNOWN_SCAR,
                Optional.empty(), Optional.empty(), "test:changed-field-service"));
        assertThrows(KnownPedestrianNavigation.RouteUnavailable.class,
                () -> KnownPedestrianRouteKnowledge.forField(changed, site,
                        changed.resourceSites().cycle(site.id()), port, start).path(start, order));
    }
}
