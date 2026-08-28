package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

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
        List<BlockPosition> diagonal = new ArrayList<>(replacement); diagonal.set(2, diagonal.get(1).offset(1, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> topology.replaceSupplyRoute(bootstrap, settlement, diagonal));
        assertThrows(IllegalArgumentException.class, () -> topology.replaceSupplyRoute(bootstrap, new SubjectId("settlement:foreign"), replacement));
    }
}
