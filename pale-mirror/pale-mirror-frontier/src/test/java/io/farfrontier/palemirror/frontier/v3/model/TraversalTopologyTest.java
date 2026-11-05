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

    @Test void linearCorridorRetainsDistinctPlanNodesWhenAReturnJourneyReusesOneSupport() {
        SurfaceAnchor start = SurfaceAnchor.at(0, 64, 0);
        TraversalTopology topology = TraversalTopology.corridor(new TraversalTopologyId("topology:return-visit"), 3L,
                new SubjectId("route:return-visit"), TraversalKind.PEDESTRIAN, Set.of(TraversalCapability.PEDESTRIAN),
                List.of(start, SurfaceAnchor.at(1, 64, 0), SurfaceAnchor.at(2, 64, 0), SurfaceAnchor.at(1, 64, 0), start));

        assertEquals(5, topology.nodes().size(), "each retained plan visit has its own stable node identity");
        assertEquals(List.of(start, SurfaceAnchor.at(1, 64, 0), SurfaceAnchor.at(2, 64, 0), SurfaceAnchor.at(1, 64, 0), start),
                topology.linearCorridorSurfaces(), "a return does not collapse the final semantic visit into its initial body position");
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

        TraversalTopology topology = routes.settlementTraversalTopology(bootstrap, settlement);

        assertEquals(FrontierRouteNetwork.expandWaypoints(routes.settlementWaypoints(bootstrap, settlement)).size(), topology.nodes().size());
        assertTrue(topology.edges().stream().allMatch(edge -> edge.kind() == TraversalKind.PEDESTRIAN
                && edge.traversableBy(TraversalCapability.PEDESTRIAN) && !edge.traversableBy(TraversalCapability.RAIL_VEHICLE)));
    }

    @Test void declaredReplacementGradeCompilesIntoSupportedThreeWideStepsWithoutTerrainDiscovery() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:topology-stepped-route"), 91L);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        List<BlockPosition> baseline = FrontierRouteNetwork.settlementWaypoints(bootstrap, settlement);
        BlockPosition origin = baseline.getFirst();
        List<BlockPosition> declared = DeclaredRouteFixtures.raised(bootstrap, RouteTopology.initial(), settlement);

        RouteTopology routes = RouteTopology.initial().replaceSettlementRoute(bootstrap, settlement, declared);
        TraversalTopology topology = routes.settlementTraversalTopology(bootstrap, settlement);
        BlockPosition stepped = topology.linearCorridorSurfaces().stream().map(SurfaceAnchor::support)
                .filter(position -> position.y() == origin.y() + 1).findFirst().orElseThrow();
        var supports = topology.linearCorridorSurfaces().stream().map(SurfaceAnchor::support).toList();
        var beforeStep = supports.get(supports.indexOf(stepped) - 1);
        BlockPosition lateral = stepped.offset(-(stepped.z() - beforeStep.z()), 0, stepped.x() - beforeStep.x());

        assertTrue(topology.edges().stream().anyMatch(edge -> edge.grade() == 1), "the retained graph contains surveyed one-block grade edges");
        assertTrue(FrontierRouteNetwork.surfaceCells(bootstrap, routes).contains(lateral), "the visible envelope follows the same raised datum as its centreline");
        assertFalse(FrontierRouteNetwork.affectedTraversalEdges(topology, lateral).isEmpty(), "loss of a raised lateral support blocks only its existing adjacent edge");
        assertThrows(IllegalArgumentException.class, () -> {
            java.util.ArrayList<BlockPosition> tooSteep = new java.util.ArrayList<>();
            tooSteep.add(origin); tooSteep.add(origin.offset(0, 9, 8)); tooSteep.addAll(baseline.subList(1, baseline.size()));
            RouteTopology.initial().replaceSettlementRoute(bootstrap, settlement, tooSteep);
        });
    }

    @Test void surveyedRaisedRouteMaterializesOwnedFootingsAndFoundationLossBlocksTheSameRetainedEdge() {
        FrontierBootstrap bootstrap = FrontierBootstrapper.create(new WorldId("frontier:topology-footing"), 91L);
        SubjectId settlement = bootstrap.settlements().getFirst().id();
        List<BlockPosition> baseline = FrontierRouteNetwork.settlementWaypoints(bootstrap, settlement);
        BlockPosition origin = baseline.getFirst();
        List<BlockPosition> declared = DeclaredRouteFixtures.raised(bootstrap, RouteTopology.initial(), settlement);
        RouteTopology routes = RouteTopology.initial().replaceSettlementRoute(bootstrap, settlement, declared);
        BlockPosition footing = FrontierRouteNetwork.foundationCells(bootstrap, routes).iterator().next();
        FrontierWorldState state = FrontierWorldState.initial(bootstrap).withRouteTopology(routes);

        assertEquals(GrayboxSemanticPart.ROUTE_FOUNDATION,
                FrontierGrayboxPlan.intactSemanticCell(bootstrap, state.hiveColony(), routes, FrontierRouteNetwork.OWNER, footing).semanticPart());
        FrontierWorldState damaged = state.recordPhysicalDelta(new PhysicalDelta(footing, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.ROUTE_NETWORK, FrontierRouteNetwork.OWNER)), java.util.Optional.of(GrayboxSemanticPart.ROUTE_FOUNDATION), "explosion:test"));

        assertFalse(damaged.routeTopology().routePassable(bootstrap, settlement),
                "a destroyed raised footing blocks the retained route graph; no hidden flat bypass exists");
        assertEquals(damaged, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(damaged)),
                "the immutable terrain plan and its route consequence survive generic snapshot recovery");
    }

    @Test void sparseSurveyChangesOnlyItsOwnedSurfaceColumnsAndSurvivesBootstrapRecovery() {
        FrontierBootstrap flat = FrontierBootstrapper.create(new WorldId("frontier:terrain-flat"), 91L);
        BlockPosition surveyedSurface = new BlockPosition(-500, 63, -500);
        TerrainSurfacePlan terrain = TerrainSurfacePlan.uniform(63)
                .withSurveyedSupport(surveyedSurface.x(), surveyedSurface.z(), 61);
        FrontierBootstrap surveyed = FrontierBootstrapper.create(new WorldId("frontier:terrain-surveyed"), 91L,
                FrontierRulesets.production(), terrain);

        assertEquals(63, surveyed.terrain().supportYAt(surveyedSurface.x() + 1, surveyedSurface.z()),
                "one survey sample never becomes an inferred height map for neighbouring columns");
        assertEquals(61, surveyed.terrain().supportYAt(surveyedSurface.x(), surveyedSurface.z()));
        assertNotEquals(flat.canonicalSha256(), surveyed.canonicalSha256(),
                "the immutable provider survey is part of the canonical bootstrap identity");

        FrontierWorldState state = FrontierWorldState.initial(surveyed);
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state));
        assertEquals(terrain, restored.bootstrap().terrain(),
                "generic snapshot recovery retains the exact sparse survey rather than replacing it with a flat default");
        assertEquals(surveyed.canonicalSha256(), restored.bootstrap().canonicalSha256());
    }

    @Test void surveyedHiveDatumCompilesOwnedHiverootWithoutAFlatFallback() {
        FrontierBootstrap flat = FrontierBootstrapper.create(new WorldId("frontier:terrain-hive-flat"), 91L);
        HiveNest west = flat.hive().seedNests().getFirst();
        List<HiveOrgan> westOrgans = flat.hive().organs().stream().filter(organ -> organ.nestId().equals(west.id())).toList();
        TerrainSurfacePlan terrain = flat.terrain();
        for (HiveOrgan organ : westOrgans) for (BlockPosition support : HiveOrganSupportPlan.baseSupportCells(organ)) {
            terrain = terrain.withSurveyedSupport(support.x(), support.z(), 67);
        }
        HiveOrgan ganglion = westOrgans.stream().filter(organ -> organ.kind() == HiveOrganKind.GANGLION).findFirst().orElseThrow();
        BlockPosition lowGanglionRoot = ganglion.anchor().offset(-2, 0, -2);
        terrain = terrain.withSurveyedSupport(lowGanglionRoot.x(), lowGanglionRoot.z(), 63);

        FrontierBootstrap surveyed = FrontierBootstrapper.create(new WorldId("frontier:terrain-hive-surveyed"), 91L,
                FrontierRulesets.production(), terrain);
        HiveNest raisedWest = surveyed.hive().seedNests().stream().filter(nest -> nest.id().equals(west.id())).findFirst().orElseThrow();
        HiveOrgan raisedGanglion = surveyed.hive().organs().stream().filter(organ -> organ.id().equals(ganglion.id())).findFirst().orElseThrow();
        FrontierWorldState initial = FrontierWorldState.initial(surveyed);
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(initial);
        BlockPosition firstRoot = new BlockPosition(lowGanglionRoot.x(), 64, lowGanglionRoot.z());

        assertEquals(68, raisedWest.anchor().y(), "a seed nest derives its datum from all declared organ supports");
        assertEquals(64, surveyed.hive().seedNests().get(1).anchor().y(), "one nest survey never shifts the other hive territory");
        assertEquals(new GrayboxCell(firstRoot, new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, raisedGanglion.id()), GrayboxMaterial.HIVE_GANGLION, GrayboxSemanticPart.FOUNDATION),
                plan.cells().get(firstRoot), "lower terrain receives organ-owned hiveroot instead of a floating graybox organ");
        FrontierWorldState damaged = initial.recordPhysicalDelta(new PhysicalDelta(firstRoot, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(new PhysicalDeltaSemanticTarget(PhysicalDeltaSemanticTargetKind.HIVE_ORGAN, raisedGanglion.id())), java.util.Optional.of(GrayboxSemanticPart.FOUNDATION), "player:test"));
        assertTrue(damaged.physicalDeltas().containsKey(firstRoot), "a broken hiveroot is an exact organ-owned physical consequence, not unowned terrain");
        FrontierWorldState restored = new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(damaged));
        assertEquals(surveyed.canonicalSha256(), restored.bootstrap().canonicalSha256(),
                "the exact elevated hive datum and terrain provider survive snapshot recovery");
        assertTrue(restored.physicalDeltas().containsKey(firstRoot));
    }
}
