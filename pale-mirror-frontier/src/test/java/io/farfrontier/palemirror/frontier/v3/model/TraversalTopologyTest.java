package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TraversalTopologyTest {
    @Test void surveyedSteppedGroundCorridorRetainsStableEdgesAndSeparatesRailCapability() {
        TraversalTopology topology = TraversalTopology.corridor(new TraversalTopologyId("topology:test"), 7L,
                new SubjectId("route:test"), TraversalKind.PEDESTRIAN,
                Set.of(TraversalCapability.PEDESTRIAN, TraversalCapability.GROUND_BIOFORM),
                List.of(SurfaceAnchor.at(4, 63, 4), SurfaceAnchor.at(5, 64, 4), SurfaceAnchor.at(6, 64, 4)));

        assertEquals(3, topology.nodes().size());
        assertEquals(2, topology.edges().size());
        assertEquals(1, topology.edges().getFirst().grade());
        assertTrue(topology.edges().getFirst().traversableBy(TraversalCapability.PEDESTRIAN));
        assertTrue(topology.edges().getFirst().traversableBy(TraversalCapability.GROUND_BIOFORM));
        assertFalse(topology.edges().getFirst().traversableBy(TraversalCapability.RAIL_VEHICLE));
    }

    @Test void topologyRejectsHiddenDiagonalAndMixedRailGroundAuthority() {
        assertThrows(IllegalArgumentException.class, () -> TraversalTopology.corridor(new TraversalTopologyId("topology:diagonal"), 0L,
                new SubjectId("route:test"), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN),
                List.of(SurfaceAnchor.at(0, 64, 0), SurfaceAnchor.at(1, 64, 1))));
        assertThrows(IllegalArgumentException.class, () -> TraversalTopology.corridor(new TraversalTopologyId("topology:mixed"), 0L,
                new SubjectId("route:test"), TraversalKind.PEDESTRIAN,
                Set.of(TraversalCapability.PEDESTRIAN, TraversalCapability.RAIL_VEHICLE),
                List.of(SurfaceAnchor.at(0, 64, 0), SurfaceAnchor.at(1, 64, 0))));
    }

    @Test void blockedOrUnknownEdgeNeverBecomesAnImplicitTraversalFallback() {
        TraversalNodeId first = new TraversalNodeId("node:first"), second = new TraversalNodeId("node:second");
        TraversalTopology topology = new TraversalTopology(new TraversalTopologyId("topology:blocked"), 4L, new SubjectId("route:test"),
                java.util.Map.of(first, SurfaceAnchor.at(0, 64, 0), second, SurfaceAnchor.at(1, 64, 0)),
                List.of(new TraversalTopology.Edge(new TraversalEdgeId("edge:blocked"), first, second, TraversalKind.PEDESTRIAN,
                        Set.of(TraversalCapability.PEDESTRIAN), 0, 2, 4L, TraversalAvailability.BLOCKED)));

        assertFalse(topology.edges().getFirst().traversableBy(TraversalCapability.PEDESTRIAN));
    }

    @Test void persistedRouteOwnerCompilesTheOnlySupplyGroundTopologyWithoutWorldDiscovery() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:topology-route"), 91L);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        RouteTopology routes = RouteTopology.initial();

        TraversalTopology topology = routes.supplyTraversalTopology(bootstrap, settlement);

        assertEquals(FrontierRouteNetwork.expandWaypoints(routes.supplyWaypoints(bootstrap, settlement)).size(), topology.nodes().size());
        assertTrue(topology.edges().stream().allMatch(edge -> edge.kind() == TraversalKind.PEDESTRIAN
                && edge.traversableBy(TraversalCapability.PEDESTRIAN) && !edge.traversableBy(TraversalCapability.RAIL_VEHICLE)));
    }
}
