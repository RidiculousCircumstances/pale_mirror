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

    @Test void fixedGridCarriagewayConsumesTheImmutableSurveyInsteadOfACodedDatum() {
        FrontierBootstrap flat = FrontierBootstrapper.create(new WorldId("frontier:grid-terrain-flat"), 91L);
        Settlement first = flat.settlements().getFirst();
        BlockPosition gridColumn = new BlockPosition(first.anchor().x(), 0, first.anchor().z() + 36);
        TerrainSurfacePlan terrain = flat.terrain().withSurveyedSupport(gridColumn.x(), gridColumn.z(), 61);
        FrontierBootstrap surveyed = FrontierBootstrapper.create(new WorldId("frontier:grid-terrain-surveyed"), 91L,
                FrontierRulesets.production(), terrain);

        assertTrue(FrontierRouteNetwork.surfaceCells(surveyed, RouteTopology.initial())
                        .contains(new BlockPosition(gridColumn.x(), 62, gridColumn.z())),
                "the fixed-grid segment takes its datum from the surveyed column");
    }

    @Test void surveyedSettlementDatumCompilesStructureAndPublicFoundationsWithoutAFlatFallback() {
        FrontierBootstrap flat = FrontierBootstrapper.create(new WorldId("frontier:terrain-settlement-flat"), 91L);
        Settlement original = flat.settlements().getFirst();
        TerrainSurfacePlan terrain = flat.terrain();
        for (SettlementStructure structure : original.structures()) {
            for (SurfaceAnchor surface : SettlementStructureFootprint.supportSurfaces(structure)) {
                terrain = terrain.withSurveyedSupport(surface.x(), surface.z(), 67);
            }
        }
        for (BlockPosition surface : SettlementLocalCirculation.surfaceCells(original)) {
            terrain = terrain.withSurveyedSupport(surface.x(), surface.z(), 67);
        }
        SettlementStructure lowerStructure = original.structures().stream().filter(structure -> structure.kind() == StructureKind.HOUSING).findFirst().orElseThrow();
        BlockPosition lowStructureColumn = SettlementStructureFootprint.supportSurfaces(lowerStructure).iterator().next().support();
        terrain = terrain.withSurveyedSupport(lowStructureColumn.x(), lowStructureColumn.z(), 63);
        BlockPosition lowPublicColumn = SettlementLocalCirculation.surfaceCells(original).stream()
                .filter(position -> original.structures().stream().flatMap(structure -> SettlementStructureFootprint.supportSurfaces(structure).stream())
                        .noneMatch(surface -> surface.x() == position.x() && surface.z() == position.z()))
                .filter(position -> !FrontierRouteNetwork.surfaceCells(flat, RouteTopology.initial()).contains(position))
                .findFirst().orElseThrow();
        terrain = terrain.withSurveyedSupport(lowPublicColumn.x(), lowPublicColumn.z(), 63);

        FrontierBootstrap surveyed = FrontierBootstrapper.create(new WorldId("frontier:terrain-settlement-surveyed"), 91L,
                FrontierRulesets.production(), terrain);
        Settlement settlement = surveyed.settlements().getFirst();
        SettlementStructure housing = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.HOUSING).findFirst().orElseThrow();
        FrontierWorldState state = FrontierWorldState.initial(surveyed);
        FrontierGrayboxPlan plan = FrontierGrayboxPlan.compile(state);
        BlockPosition structureFooting = new BlockPosition(lowStructureColumn.x(), 64, lowStructureColumn.z());
        BlockPosition publicFooting = new BlockPosition(lowPublicColumn.x(), 64, lowPublicColumn.z());

        assertEquals(68, settlement.anchor().y(), "the surveyed settlement selects its datum from its declared structures and public approaches");
        assertEquals(64, surveyed.settlements().get(1).anchor().y(), "one settlement survey never shifts another site");
        assertEquals(new GrayboxCell(structureFooting, housing.id(), GrayboxMaterial.HOUSING, GrayboxSemanticPart.FOUNDATION),
                plan.cells().get(structureFooting), "a lower declared column has a structure-owned vertical foundation");
        assertEquals(new GrayboxCell(publicFooting, settlement.id(), GrayboxMaterial.ROUTE_FOUNDATION, GrayboxSemanticPart.FOUNDATION),
                plan.cells().get(publicFooting), "the same immutable site plan supports its public approach instead of floating it");
        SettlementStructure infirmary = settlement.structures().stream().filter(structure -> structure.kind() == StructureKind.INFIRMARY).findFirst().orElseThrow();
        assertEquals(68, SettlementInfirmaryTreatmentPort.forInfirmary(infirmary).exteriorApproachSurface().y(),
                "facility ports retain the site datum instead of a separate flat access convention");
        assertTrue(FrontierTraversalPlan.compile(state).facilities().containsKey(infirmary.id()),
                "the terrain-aware facility remains bound to its one canonical public topology");

        FrontierWorldState damaged = state.recordPhysicalDelta(new PhysicalDelta(structureFooting, PhysicalDeltaKind.KNOWN_SEMANTIC_LOSS,
                java.util.Optional.of(housing.id()), java.util.Optional.of(GrayboxSemanticPart.FOUNDATION), "player:test"));
        assertEquals(StructureCondition.DAMAGED, damaged.structureConditions().get(housing.id()),
                "foundation loss is a structural consequence, not a materializer-local hole");
        assertEquals(damaged, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(damaged)),
                "the distinct facility datum, footing loss and consequence survive recovery");
    }

    @Test void elevatedSettlementOwnsBoundedResidentApronAndGradeCheckedNaturalIngress() {
        FrontierBootstrap flat = FrontierBootstrapper.create(new WorldId("frontier:terrain-resident-ingress-flat"), 91L);
        Settlement original = flat.settlements().getFirst();
        TerrainSurfacePlan terrain = flat.terrain();
        for (SettlementStructure structure : original.structures()) {
            for (SurfaceAnchor surface : SettlementStructureFootprint.supportSurfaces(structure)) {
                terrain = terrain.withSurveyedSupport(surface.x(), surface.z(), 67);
            }
        }
        for (BlockPosition surface : SettlementLocalCirculation.surfaceCells(original)) {
            terrain = terrain.withSurveyedSupport(surface.x(), surface.z(), 67);
        }
        FrontierBootstrap surveyed = FrontierBootstrapper.create(new WorldId("frontier:terrain-resident-ingress"), 91L,
                FrontierRulesets.production(), terrain);
        Settlement settlement = surveyed.settlements().getFirst();
        int beds = surveyed.ruleset().facilityCapacity().intactHousingBeds();
        SettlementResidentIngressPlan.Plan ingress = SettlementResidentIngressPlan.compile(surveyed.bounds(), terrain, settlement, beds);
        FrontierWorldState state = FrontierWorldState.initial(surveyed);
        FrontierGrayboxPlan graybox = FrontierGrayboxPlan.compile(state);

        assertEquals(beds, ingress.homeSlots().size());
        assertTrue(ingress.homeSlots().stream().allMatch(position -> position.y() == settlement.anchor().y()),
                "every exact resident home occupies the raised settlement apron, not an unplanned natural-height ring");
        assertTrue(ingress.topology().edges().stream().anyMatch(edge -> edge.grade() == 1),
                "the external ingress retains actual one-block grade edges to its surveyed natural endpoint");
        BlockPosition homeFooting = ingress.homeSlots().getFirst().offset(0, -4, 0);
        assertEquals(new GrayboxCell(homeFooting, settlement.id(), GrayboxMaterial.ROUTE_FOUNDATION, GrayboxSemanticPart.FOUNDATION),
                graybox.cells().get(homeFooting), "the raised resident apron owns its complete lower support fill");
        assertEquals(new GrayboxCell(ingress.homeSlots().getFirst(), settlement.id(), GrayboxMaterial.ROUTE,
                        GrayboxSemanticPart.PUBLIC_ACCESS_SURFACE), graybox.cells().get(ingress.homeSlots().getFirst()),
                "home surfaces are materialized public support, not abstract actor coordinates");
        assertTrue(FrontierTraversalPlan.compile(state).topologies().containsKey(ingress.topology().id()),
                "the ingress is retained as a named pedestrian topology instead of a navigator-only ramp");
        assertEquals(state, new FrontierWorldStateCodec().decode(new FrontierWorldStateCodec().encode(state)),
                "the resident apron is derived again from the same immutable terrain/bootstrap contract after recovery");
    }

    @Test void residentIngressFailsClosedWhenNoSurveyedNaturalSupportIsReachableAtAValidGrade() {
        FrontierBootstrap flat = FrontierBootstrapper.create(new WorldId("frontier:terrain-resident-ingress-blocked-flat"), 91L);
        Settlement original = flat.settlements().getFirst();
        TerrainSurfacePlan terrain = flat.terrain();
        for (SettlementStructure structure : original.structures()) {
            for (SurfaceAnchor surface : SettlementStructureFootprint.supportSurfaces(structure)) {
                terrain = terrain.withSurveyedSupport(surface.x(), surface.z(), 67);
            }
        }
        for (BlockPosition surface : SettlementLocalCirculation.surfaceCells(original)) {
            terrain = terrain.withSurveyedSupport(surface.x(), surface.z(), 67);
        }
        FrontierBootstrap surveyed = FrontierBootstrapper.create(new WorldId("frontier:terrain-resident-ingress-blocked"), 91L,
                FrontierRulesets.production(), terrain);
        Settlement settlement = surveyed.settlements().getFirst();
        FacilityFacing facing = SettlementAccessPort.forHall(settlement.structures().stream()
                .filter(structure -> structure.kind() == StructureKind.HALL).findFirst().orElseThrow()).facing();
        // The plan's declared 30-cell apron is deliberately bounded; no farther surveyed
        // column may cause it to manufacture a longer or alternate ingress.
        BlockPosition gate = settlement.anchor().offset(facing.x() * 30, 0, facing.z() * 30);
        for (int step = 1; step <= 32; step++) {
            terrain = terrain.withSurveyedSupport(gate.x() + facing.x() * step, gate.z() + facing.z() * step, 100);
        }

        TerrainSurfacePlan unreachableTerrain = terrain;
        assertThrows(IllegalArgumentException.class, () -> SettlementResidentIngressPlan.compile(surveyed.bounds(), unreachableTerrain,
                settlement, surveyed.ruleset().facilityCapacity().intactHousingBeds()),
                "the compiler must reject an unreachable site instead of cutting an unbounded ramp or inventing another entrance");
    }
}
