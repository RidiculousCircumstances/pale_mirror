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
        replacement.add(baseline.get(1).offset(0, 0, 10)); replacement.add(new BlockPosition(-375, baseline.get(1).y(), baseline.get(1).z() + 10));
        replacement.add(new BlockPosition(-375, baseline.get(1).y(), baseline.get(1).z())); replacement.addAll(baseline.subList(2, baseline.size()));
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
}
