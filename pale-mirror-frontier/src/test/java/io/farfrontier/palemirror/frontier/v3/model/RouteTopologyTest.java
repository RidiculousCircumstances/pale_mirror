package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentKind;
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
                baseline.get(2).offset(-10, 0, 0), baseline.get(2), baseline.get(3), baseline.get(4), baseline.get(5));
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
                List.of(baseline.get(0), baseline.get(1), baseline.get(1).offset(-10, 0, 0), baseline.get(2).offset(-10, 0, 0), baseline.get(2), baseline.get(3), baseline.get(4), baseline.get(5)),
                0, RouteConstructionStatus.BUILDING);
        SubjectId materialId = new SubjectId("item:route-work-concrete");
        FrontierWorldState state = RouteConstructionStateSupport.begin(FrontierWorldState.initial(bootstrap), project).withInventory(FrontierWorldState.initial(bootstrap).inventory());
        state = state.withInventory(state.inventory().withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.PREPARED)
                .withSurfaceStatus(FrontierRouteNetwork.MAINTENANCE_CONTAINER, ContainerSurfaceStatus.ACTIVE)
                .store(new ExactItemStack(materialId, FrontierRouteNetwork.OWNER, "minecraft:gray_concrete", 2, new InventoryCustody.ContainerSlot(FrontierRouteNetwork.MAINTENANCE_CONTAINER, 0))));
        var events = RouteConstructionProcess.plan(state, RouteConstructionProcess.scan(1, 900L));
        PhysicalIntentPrepared loading = events.stream().map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance)
                .map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        assertEquals(PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING, loading.intent().kind());
        assertTrue(loading.intent().subjectIds().contains(project.id()));
        FrontierWorldState conflicted = state.preparePhysicalIntent(loading.intent())
                .transitionPhysicalIntent(loading.intent().id(), PhysicalIntentStatus.UNKNOWN_AFTER_RESTART, Optional.empty());
        assertEquals(RouteConstructionStatus.CONFLICT, conflicted.routeConstructions().get(project.id()).status());
        state = state.preparePhysicalIntent(loading.intent())
                .transitionPhysicalIntent(loading.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        SubjectId cargoId = loading.intent().subjectIds().get(2), cargoItemId = loading.intent().subjectIds().get(3), sourceItemId = loading.intent().subjectIds().get(4);
        state = state.transitionPhysicalIntent(loading.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(new RouteConstructionMaterialLoadObservation(
                new PhysicalObservationId("observation:route-work-load"), loading.intent().id(), project.id(), cargoId, sourceItemId, cargoItemId, 1)));
        state = RouteConstructionStateSupport.reduceMaterialLoaded(state, FrontierRouteNetwork.OWNER,
                new RouteConstructionMaterialLoaded(project.id(), new CargoBatch(cargoId, FrontierRouteNetwork.OWNER, List.of(cargoItemId))));
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)),
                "a restart must retain the project-to-COLD-cargo binding before a cell is built");
        PhysicalIntentPrepared prepared = RouteConstructionProcess.plan(state, RouteConstructionProcess.scan(2, 1_000L)).stream()
                .map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        assertEquals(PhysicalIntentKind.ROUTE_CONSTRUCTION, prepared.intent().kind());
        state = RouteConstructionProcess.reducePrepared(state, FrontierRouteNetwork.OWNER, prepared.intent())
                .transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.RUNNING, Optional.empty());
        BlockPosition position = FrontierGrayboxPlan.routeConstructionCells(state, project).getFirst();
        state = state.transitionPhysicalIntent(prepared.intent().id(), PhysicalIntentStatus.CONFIRMED, Optional.of(new RouteConstructionObservation(
                new PhysicalObservationId("observation:route-work"), prepared.intent().id(), project.id(), cargoItemId, position)));
        assertEquals(1, state.routeConstructions().get(project.id()).confirmedCells());
        assertEquals(1, state.inventory().items().get(materialId).count());
        assertTrue(state.routeConstructions().get(project.id()).cargoId().isEmpty(),
                "one completed cell must retire its single-unit cargo rather than strand a hidden remainder");
        assertTrue(!state.inventory().cargo().containsKey(cargoId) && !state.inventory().items().containsKey(cargoItemId),
                "the consumed cargo identity must not retain stock after its one physical block is placed");
        PhysicalIntentPrepared nextLoading = RouteConstructionProcess.plan(state, RouteConstructionProcess.scan(3, 1_100L)).stream()
                .map(event -> event.payload()).filter(PhysicalIntentPrepared.class::isInstance).map(PhysicalIntentPrepared.class::cast).findFirst().orElseThrow();
        assertEquals(PhysicalIntentKind.ROUTE_CONSTRUCTION_MATERIAL_LOADING, nextLoading.intent().kind());
        assertEquals(materialId, nextLoading.intent().subjectIds().get(4), "the remainder stays in the original maintenance stack");
        assertTrue(!nextLoading.intent().subjectIds().get(2).equals(cargoId), "each extracted physical unit receives a fresh cargo identity");
        assertTrue(!nextLoading.intent().id().equals(loading.intent().id()),
                "a later material pickup must never reuse the completed intent identity");
        assertEquals(RouteTopology.initial(), state.routeTopology());
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)));
    }

    @Test
    void onlyAReadyCandidateCanCutOverTheCanonicalRouteTopology() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-cutover"), 96L);
        SubjectId settlement = bootstrap.settlements().getFirst().id(); List<BlockPosition> baseline = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement);
        List<BlockPosition> replacement = List.of(baseline.get(0), baseline.get(1), baseline.get(1).offset(-10, 0, 0), baseline.get(2).offset(-10, 0, 0),
                baseline.get(2), baseline.get(3), baseline.get(4), baseline.get(5));
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
        assertTrue(first.stream().noneMatch(event -> event.payload() instanceof RouteConstructionStarted));
    }

    @Test
    void everySettlementCanBypassItsMaterializedEgressWithoutAdoptingWorldGeometry() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:route-reroute-all"), 98L);
        for (Settlement settlement : bootstrap.settlements()) {
            BlockPosition lossPosition = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement.id()).get(1);
            FrontierWorldState damaged = FrontierWorldState.initial(bootstrap).recordPhysicalDelta(new PhysicalDelta(lossPosition,
                    PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS, Optional.of(FrontierRouteNetwork.OWNER), Optional.of(GrayboxSemanticPart.ROUTE_SURFACE), "blast:test"));
            assertTrue(RouteConstructionProcess.plan(damaged, RouteConstructionProcess.scan(1, 900L)).stream()
                    .noneMatch(event -> event.payload() instanceof RouteConstructionStarted));
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
