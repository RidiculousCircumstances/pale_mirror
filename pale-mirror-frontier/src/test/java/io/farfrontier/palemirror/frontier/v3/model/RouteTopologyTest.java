package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentStatus;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTopologyTest {
    @Test
    void acceptedReplacementIsBoundedCanonicalAndCannotBeAnArbitraryPlayerPath() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-topology"), 91L);
        SubjectId settlement = bootstrap.settlements().getFirst().id(); List<BlockPosition> baseline = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement);
        List<BlockPosition> replacement = new ArrayList<>();
        replacement.add(baseline.get(0)); replacement.add(baseline.get(1));
        replacement.add(baseline.get(1).offset(-10, 0, 0)); replacement.add(baseline.get(2).offset(-10, 0, 0));
        replacement.add(baseline.get(2)); replacement.addAll(baseline.subList(3, baseline.size()));
        RouteTopology topology = RouteTopology.initial().replaceSupplyRoute(bootstrap, settlement, replacement);
        assertEquals(replacement, topology.supplyWaypoints(bootstrap, settlement));
        assertEquals(0, RouteTopology.initial().replacementSupplyRoutes().size());
        FrontierWorldState state = FrontierWorldState.initial(bootstrap).withRouteTopology(topology);
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        assertEquals(topology, state.withInventory(state.inventory()).routeTopology());
        assertEquals(GrayboxMaterial.ROUTE, FrontierGrayboxPlan.compile(state).cells().get(replacement.get(2)).material());
        assertEquals(GrayboxSemanticPart.ROUTE_SURFACE,
                FrontierGrayboxPlan.intactSemanticCell(bootstrap, state.hiveColony(), topology, FrontierRouteNetwork.OWNER, replacement.get(2)).semanticPart());
        BlockPosition retiredCell = new BlockPosition((baseline.get(1).x() + baseline.get(2).x()) / 2, baseline.get(1).y(), baseline.get(1).z());
        FrontierWorldState retainedLoss = FrontierWorldState.initial(bootstrap).recordPhysicalDelta(new PhysicalDelta(retiredCell,
                PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "explosion:test"))
                .withRouteTopology(topology);
        assertEquals(1, retainedLoss.physicalDeltas().size());
        assertEquals(null, FrontierGrayboxPlan.compile(retainedLoss).cells().get(retiredCell));
        List<BlockPosition> diagonal = new ArrayList<>(replacement); diagonal.set(2, diagonal.get(1).offset(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> topology.replaceSupplyRoute(bootstrap, settlement, diagonal));
        assertThrows(IllegalArgumentException.class, () -> topology.replaceSupplyRoute(bootstrap, new SubjectId("settlement:foreign"), replacement));
    }

    @Test
    void codecRejectsOutOfBoundsRouteCountBeforeReadingUnboundedInput() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-topology-codec"), 92L);
        assertThrows(IllegalArgumentException.class, () -> RouteTopologyStateCodec.read(
                new DataInputStream(new ByteArrayInputStream(new byte[] {(byte) (RouteTopology.MAX_REPLACEMENTS + 1)})), bootstrap));
    }

    @Test
    void topologyProjectsEverySettlementSupplyCorridor() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-topology-network"), 93L);
        FrontierWorldState state = FrontierWorldState.initial(bootstrap);
        for (var settlement : bootstrap.settlements()) {
            BlockPosition corridor = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement.id()).get(2);
            assertEquals(GrayboxMaterial.ROUTE, FrontierGrayboxPlan.compile(state).cells().get(corridor).material());
        }
    }

    @Test
    void inactiveConstructionIsBoundedPersistedAndCannotBecomeTopologyByAccident() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-construction"), 94L);
        SubjectId settlement = bootstrap.settlements().getFirst().id(); List<BlockPosition> baseline = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement);
        List<BlockPosition> replacement = List.of(baseline.get(0), baseline.get(1), baseline.get(1).offset(-10, 0, 0),
                baseline.get(2).offset(-10, 0, 0), baseline.get(2), baseline.get(3), baseline.get(4), baseline.get(5), baseline.get(6));
        RouteConstruction project = new RouteConstruction(new SubjectId("construction:route-1"), settlement, replacement, 0, RouteConstructionStatus.BUILDING);
        FrontierWorldState state = RouteConstructionStateSupport.begin(FrontierWorldState.initial(bootstrap), project);
        assertEquals(project, state.routeConstructions().get(project.id()));
        assertEquals(FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement), state.routeTopology().supplyWaypoints(bootstrap, settlement));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
        assertThrows(IllegalArgumentException.class, () -> RouteConstructionStateSupport.begin(state, project));
    }

    @Test
    void inactiveConstructionConsumesOneExactRouteMaterialPerConfirmedCell() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-construction-work"), 95L);
        SubjectId settlement = bootstrap.settlements().getFirst().id(); List<BlockPosition> baseline = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement);
        RouteConstruction project = new RouteConstruction(new SubjectId("construction:route-work"), settlement,
                List.of(baseline.get(0), baseline.get(1), baseline.get(1).offset(-10, 0, 0), baseline.get(2).offset(-10, 0, 0), baseline.get(2), baseline.get(3), baseline.get(4), baseline.get(5), baseline.get(6)),
                0, RouteConstructionStatus.BUILDING);
        SubjectId materialId = new SubjectId("item:route-work-concrete");
        FrontierWorldState state = RouteConstructionStateSupport.begin(FrontierWorldState.initial(bootstrap), project).withInventory(FrontierWorldState.initial(bootstrap).inventory());
        state = state.withInventory(state.inventory().withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(materialId, "minecraft:gray_concrete", 2, new InventoryCustody.ContainerSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER, 0))));
        var events = RouteConstructionProcess.plan(state, RouteConstructionProcess.scan(1, 900L));
        PhysicalIntentPrepared prepared = events.stream().map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        assertTrue(prepared.intent().subjectIds().contains(project.id()));
        FrontierWorldState conflicted = RouteConstructionProcess.reducePrepared(state, FrontierRouteNetwork.OWNER, prepared.intent())
                .transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        assertEquals(RouteConstructionStatus.CONFLICT, conflicted.routeConstructions().get(project.id()).status());
        state = RouteConstructionProcess.reducePrepared(state, FrontierRouteNetwork.OWNER, prepared.intent())
                .transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        BlockPosition position = FrontierGrayboxPlan.routeConstructionCells(state, project).getFirst();
        state = state.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(new RouteConstructionObservation(
                new PhysicalObservationId("observation:route-work"), prepared.intent().id(), project.id(), materialId, position)));
        assertEquals(1, state.routeConstructions().get(project.id()).confirmedCells());
        assertEquals(1, state.inventory().items().get(materialId).count());
        assertEquals(RouteTopology.initial(), state.routeTopology());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void onlyAReadyCandidateCanCutOverTheCanonicalRouteTopology() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-cutover"), 96L);
        SubjectId settlement = bootstrap.settlements().getFirst().id(); List<BlockPosition> baseline = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement);
        List<BlockPosition> replacement = List.of(baseline.get(0), baseline.get(1), baseline.get(1).offset(-10, 0, 0), baseline.get(2).offset(-10, 0, 0),
                baseline.get(2), baseline.get(3), baseline.get(4), baseline.get(5), baseline.get(6));
        int required = FrontierRouteNetwork.constructionCells(bootstrap, RouteTopology.initial(), settlement, replacement).size();
        RouteConstruction ready = new RouteConstruction(new SubjectId("construction:cutover"), settlement, replacement, required, RouteConstructionStatus.READY);
        FrontierWorldState state = RouteConstructionStateSupport.begin(FrontierWorldState.initial(bootstrap), ready);
        FrontierWorldState cutOver = RouteConstructionStateSupport.cutover(state, ready.id());
        assertEquals(replacement, cutOver.routeTopology().supplyWaypoints(bootstrap, settlement));
        assertTrue(cutOver.routeConstructions().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> RouteConstructionStateSupport.cutover(state, new SubjectId("construction:missing")));
    }

    @Test
    void observedRouteLossDeterministicallyStartsACanonicalPassableBypass() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-reroute"), 97L);
        SubjectId settlement = bootstrap.settlements().getFirst().id(); List<BlockPosition> baseline = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement);
        BlockPosition lossPosition = baseline.get(1);
        FrontierWorldState damaged = FrontierWorldState.initial(bootstrap).recordPhysicalDelta(new PhysicalDelta(lossPosition,
                PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "blast:test"));
        var first = RouteConstructionProcess.plan(damaged, RouteConstructionProcess.scan(1, 900L));
        var second = RouteConstructionProcess.plan(damaged, RouteConstructionProcess.scan(1, 900L));
        assertEquals(first, second);
        RouteConstructionStarted started = first.stream().map(event -> event.payload()).filter(RouteConstructionStarted.class::isInstance)
                .map(RouteConstructionStarted.class::cast).findFirst().orElseThrow();
        RouteConstruction candidate = started.project();
        assertEquals(settlement, candidate.settlementId());
        assertEquals(baseline, damaged.routeTopology().supplyWaypoints(bootstrap, settlement));
        assertTrue(!FrontierGrayboxPlan.routeConstructionCells(damaged, candidate).isEmpty());
        assertTrue(FrontierRouteNetwork.isPassable(bootstrap, candidate.waypoints(), damaged.physicalDeltas()));
        FrontierWorldState startedState = RouteConstructionStateSupport.reduceStarted(damaged, FrontierRouteNetwork.OWNER, started);
        assertEquals(candidate, startedState.routeConstructions().get(candidate.id()));
        RouteTopology cutover = damaged.routeTopology().replaceSupplyRoute(bootstrap, settlement, candidate.waypoints());
        assertTrue(FrontierGrayboxPlan.compile(damaged.withRouteTopology(cutover)).cells().size() > 0);
    }

    @Test
    void everySettlementCanBypassItsMaterializedEgressWithoutAdoptingWorldGeometry() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-reroute-all"), 98L);
        for (Settlement settlement : bootstrap.settlements()) {
            BlockPosition lossPosition = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement.id()).get(1);
            FrontierWorldState damaged = FrontierWorldState.initial(bootstrap).recordPhysicalDelta(new PhysicalDelta(lossPosition,
                    PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "blast:test"));
            RouteConstructionStarted started = RouteConstructionProcess.plan(damaged, RouteConstructionProcess.scan(1, 900L)).stream()
                    .map(event -> event.payload()).filter(RouteConstructionStarted.class::isInstance).map(RouteConstructionStarted.class::cast).findFirst().orElseThrow();
            assertEquals(settlement.id(), started.project().settlementId());
            assertTrue(FrontierRouteNetwork.isPassable(bootstrap, started.project().waypoints(), damaged.physicalDeltas()));
        }
    }

    @Test
    void blockedRequiredDestinationDoesNotProduceAFictitiousReroute() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-reroute-destination"), 99L);
        SubjectId settlement = bootstrap.settlements().getFirst().id(); BlockPosition destination = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement).getLast();
        FrontierWorldState damaged = FrontierWorldState.initial(bootstrap).recordPhysicalDelta(new PhysicalDelta(destination,
                PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "blast:test"));
        assertTrue(RouteConstructionProcess.plan(damaged, RouteConstructionProcess.scan(1, 900L)).stream()
                .noneMatch(event -> event.payload() instanceof RouteConstructionStarted));
    }
}
