package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import io.farfrontier.palemirror.frontier.v3.persistence.FrontierWorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    @Test void declaredReplacementGradeCompilesIntoSupportedThreeWideStepsWithoutTerrainDiscovery() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:topology-stepped-route"), 91L);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        List<BlockPosition> baseline = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement);
        BlockPosition origin = baseline.getFirst();
        BlockPosition crest = origin.offset(0, 2, 8);
        java.util.ArrayList<BlockPosition> declared = new java.util.ArrayList<>();
        declared.add(origin); declared.add(crest); declared.addAll(baseline.subList(1, baseline.size()));

        RouteTopology routes = RouteTopology.initial().replaceSupplyRoute(bootstrap, settlement, declared);
        TraversalTopology topology = routes.supplyTraversalTopology(bootstrap, settlement);
        BlockPosition stepped = topology.linearCorridorSurfaces().stream().map(SurfaceAnchor::support)
                .filter(position -> position.y() == origin.y() + 1).findFirst().orElseThrow();
        BlockPosition lateral = stepped.offset(1, 0, 0);

        assertTrue(topology.edges().stream().anyMatch(edge -> edge.grade() == 1), "the retained graph contains surveyed one-block grade edges");
        assertTrue(FrontierRouteNetwork.surfaceCells(bootstrap, routes).contains(lateral), "the visible envelope follows the same raised datum as its centreline");
        assertFalse(FrontierRouteNetwork.affectedTraversalEdges(topology, lateral).isEmpty(), "loss of a raised lateral support blocks only its existing adjacent edge");
        assertThrows(IllegalArgumentException.class, () -> {
            java.util.ArrayList<BlockPosition> tooSteep = new java.util.ArrayList<>();
            tooSteep.add(origin); tooSteep.add(origin.offset(0, 9, 8)); tooSteep.addAll(baseline.subList(1, baseline.size()));
            RouteTopology.initial().replaceSupplyRoute(bootstrap, settlement, tooSteep);
        });
    }

    @Test void surveyedRaisedRouteMaterializesOwnedFootingsAndFoundationLossBlocksTheSameRetainedEdge() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:topology-footing"), 91L);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        List<BlockPosition> baseline = FrontierRouteNetwork.supplyWaypoints(bootstrap, settlement);
        BlockPosition origin = baseline.getFirst();
        List<BlockPosition> declared = new java.util.ArrayList<>();
        declared.add(origin); declared.add(origin.offset(0, 2, 8)); declared.addAll(baseline.subList(1, baseline.size()));
        RouteTopology routes = RouteTopology.initial().replaceSupplyRoute(bootstrap, settlement, declared);
        BlockPosition footing = FrontierRouteNetwork.foundationCells(bootstrap, routes).iterator().next();
        FrontierWorldState state = FrontierWorldState.initial(bootstrap).withRouteTopology(routes);

        assertEquals(GrayboxSemanticPart.ROUTE_FOUNDATION,
                FrontierGrayboxPlan.intactSemanticCell(bootstrap, state.hiveColony(), routes, FrontierRouteNetwork.OWNER, footing).semanticPart());
        FrontierWorldState damaged = state.recordPhysicalDelta(new PhysicalDelta(footing, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(FrontierRouteNetwork.OWNER), java.util.Optional.of(GrayboxSemanticPart.ROUTE_FOUNDATION), "explosion:test"));

        assertFalse(damaged.routeTopology().supplyPassable(bootstrap, settlement),
                "a destroyed raised footing blocks the retained route graph; no hidden flat bypass exists");
        assertEquals(damaged, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(damaged)),
                "the immutable terrain plan and its route consequence survive generic snapshot recovery");
    }

    @Test void sparseSurveyChangesOnlyItsOwnedSurfaceColumnsAndSurvivesBootstrapRecovery() {
        FrontierBootstrap flat = FrontierBootstrapper.create(new WorldId("frontier:terrain-flat"), 91L);
        SubjectId settlement = flat.settlements().getFirst().id();
        BlockPosition surveyedSurface = FrontierRouteNetwork.supplyWaypoints(flat, settlement).getFirst();
        TerrainSurfacePlan terrain = TerrainSurfacePlan.uniform(63)
                .withSurveyedSupport(surveyedSurface.x(), surveyedSurface.z(), 61);
        FrontierBootstrap surveyed = FrontierBootstrapper.create(new WorldId("frontier:terrain-surveyed"), 91L,
                FrontierRulesets.production(), terrain);
        RouteTopology routes = RouteTopology.initial();
        BlockPosition lowerFooting = new BlockPosition(surveyedSurface.x(), 62, surveyedSurface.z());
        BlockPosition upperFooting = new BlockPosition(surveyedSurface.x(), 63, surveyedSurface.z());

        assertEquals(63, surveyed.terrain().supportYAt(surveyedSurface.x() + 1, surveyedSurface.z()),
                "one survey sample never becomes an inferred height map for neighbouring columns");
        assertTrue(FrontierRouteNetwork.foundationCells(surveyed, routes).contains(lowerFooting));
        assertTrue(FrontierRouteNetwork.foundationCells(surveyed, routes).contains(upperFooting));
        assertNotEquals(flat.canonicalSha256(), surveyed.canonicalSha256(),
                "the immutable provider survey is part of the canonical bootstrap identity");

        FrontierWorldState state = FrontierWorldState.initial(surveyed);
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(terrain, restored.bootstrap().terrain(),
                "generic snapshot recovery retains the exact sparse survey rather than replacing it with a flat default");
        assertEquals(surveyed.canonicalSha256(), restored.bootstrap().canonicalSha256());
    }
}
