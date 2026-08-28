package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}
