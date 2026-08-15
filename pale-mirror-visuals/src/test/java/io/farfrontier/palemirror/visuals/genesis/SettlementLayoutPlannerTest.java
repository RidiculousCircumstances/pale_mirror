package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.LinearFeatureKind;
import io.farfrontier.palemirror.api.PerimeterModuleKind;
import io.farfrontier.palemirror.api.SettlementLayoutArchetype;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPortKind;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

class SettlementLayoutPlannerTest {
    private final SettlementLayoutPlanner planner = new SettlementLayoutPlanner();
    private final VisualPoint anchor = new VisualPoint(800, 72, -400);

    @Test void terrainReliefSelectsTheThreeReusableArchetypes() {
        assertEquals(SettlementLayoutArchetype.FREIGHT_CROSSROADS, plan(2).archetype());
        assertEquals(SettlementLayoutArchetype.FOOTHILL_RIBBON, plan(7).archetype());
        assertEquals(SettlementLayoutArchetype.TERRACED_BASIN, plan(14).archetype());
    }

    @Test void everyArchetypeIsCompactConnectedAndSegmentDefended() {
        for (int relief : new int[]{2, 7, 14}) {
            var plan = plan(relief);
            assertEquals(20, plan.modules().size());
            assertEquals(plan.modules().size(), plan.foundations().size());
            assertTrue(plan.bounds().max().x() - plan.bounds().min().x() <= 192);
            assertTrue(plan.bounds().max().z() - plan.bounds().min().z() <= 192);
            var circulationColumns = new HashSet<String>();
            plan.circulation().forEach(feature -> feature.nodes().forEach(point ->
                    circulationColumns.add(point.x() + ":" + point.z())));
            plan.modules().forEach(module -> {
                var entrance = module.ports().stream().filter(port -> port.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                        .findFirst().orElseThrow().position();
                assertTrue(circulationColumns.contains(entrance.x() + ":" + entrance.z()),
                        module.instanceId() + " has no road endpoint");
            });
            assertFalse(plan.defences().isEmpty());
            assertTrue(plan.defences().stream().allMatch(feature -> feature.kind() == LinearFeatureKind.PALISADE
                    || feature.kind() == LinearFeatureKind.PALISADE_GATE
                    || feature.kind() == LinearFeatureKind.DITCH || feature.kind() == LinearFeatureKind.RETAINING_WALL));
            assertEquals(4, plan.defences().stream()
                    .filter(feature -> feature.kind() == LinearFeatureKind.PALISADE_GATE).count(),
                    "the enclosure must expose four deliberate traversable gates");
            assertTrue(plan.defences().stream()
                            .filter(feature -> feature.kind() == LinearFeatureKind.PALISADE
                                    || feature.kind() == LinearFeatureKind.PALISADE_GATE)
                            .mapToInt(SettlementLayoutPlannerTest::horizontalLength).sum() >= 500,
                    "the authored enclosure must visibly protect the whole occupied district");
            assertTrue(plan.defences().stream()
                    .filter(feature -> feature.kind() == LinearFeatureKind.PALISADE_GATE)
                    .anyMatch(feature -> crosses(feature, plan.freightGate())),
                    "the public freight arch and defensive opening must be one gateway");
            plan.defences().stream()
                    .filter(feature -> feature.kind() == LinearFeatureKind.PALISADE
                            || feature.kind() == LinearFeatureKind.PALISADE_GATE)
                    .flatMap(feature -> raster(feature).stream()).forEach(point ->
                            plan.buildings().forEach(building -> assertFalse(
                                    containsHorizontal(building.parcel(), point),
                                    building.buildingId() + " intersects the structural perimeter at " + point)));
            assertEquals(5, plan.expansionPlots().size());
            assertEquals(7, plan.developmentReservations().size());
            assertEquals(7, plan.developmentReservations().stream().map(value -> value.id()).distinct().count());
            assertEquals(6, plan.openSpaces().size());
            assertEquals(6, plan.openSpaces().stream().map(value -> value.id()).distinct().count());
            plan.openSpaces().forEach(space -> {
                var conflict = plan.buildings().stream()
                        .filter(building -> overlaps(space.bounds(), building.parcel())).findFirst().orElse(null);
                assertTrue(conflict == null, () -> space.id() + " overlaps "
                        + (conflict == null ? "no building" : conflict.buildingId()));
            });
            assertEquals(48, plan.buildings().stream()
                    .mapToInt(building -> building.capacity(io.farfrontier.palemirror.api.BuildingSlotKind.BED)).sum());
            plan.expansionPlots().forEach(plot -> assertTrue(plan.modules().stream()
                    .noneMatch(module -> overlaps(plot, module.footprint())), "reserved plot overlaps a building"));
            plan.developmentReservations().forEach(reservation -> assertTrue(
                    contains(plan.bounds(), reservation.bounds()), reservation.id() + " escaped the masterplan"));
            plan.developmentReservations().forEach(reservation -> {
                assertTrue(plan.managedArea().contains(reservation.bounds().min().x(), reservation.bounds().min().z()),
                        reservation.id() + " escaped the managed district at its minimum corner");
                assertTrue(plan.managedArea().contains(reservation.bounds().max().x(), reservation.bounds().max().z()),
                        reservation.id() + " escaped the managed district at its maximum corner");
            });
            assertTrue(plan.managedArea().contains(anchor.x(), anchor.z()),
                    "the civic center must belong to the managed district union");
            assertTrue(plan.managedArea().areas().size() > 1,
                    "the managed district must remain a semantic union rather than one rectangle");
            plan.developmentReservations().stream()
                    .filter(value -> value.kind() == io.farfrontier.palemirror.api.DevelopmentReservationKind.ANNEX)
                    .forEach(annex -> {
                        assertTrue(plan.buildings().stream()
                                .filter(value -> !value.buildingId().equals(annex.ownerBuildingId()))
                                .noneMatch(value -> overlaps(annex.bounds(), value.parcel())),
                                annex.id() + " overlaps another functional building");
                        assertTrue(plan.openSpaces().stream()
                                .noneMatch(value -> overlaps(annex.bounds(), value.bounds())),
                                annex.id() + " overlaps an authored open space");
                    });
            assertEquals(128, plan.environment().hardRadius());
            assertEquals(176, plan.environment().transitionRadius());
            assertEquals(16, plan.environment().structureClearance());
            assertEquals(1, plan.perimeter().modules().stream()
                    .filter(value -> value.kind() == PerimeterModuleKind.FREIGHT_GATE).count());
            assertEquals(3, plan.perimeter().modules().stream()
                    .filter(value -> value.kind() == PerimeterModuleKind.PEDESTRIAN_GATE).count());
            assertEquals(plan.surfacePlan().columns().size(), plan.surfacePlan().columns().stream()
                    .map(value -> value.x() + ":" + value.z()).distinct().count(),
                    "every authored surface column must have exactly one final owner");
            plan.modules().stream().flatMap(value -> value.ports().stream())
                    .filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                    .forEach(value -> plan.surfacePlan().require(value.position().x(), value.position().z()));
        }
    }

    @Test void aFlatFeatureClassifiedAsStairsStillCompilesAsLevelPaving() {
        var points = java.util.List.of(new VisualPoint(0, 70, 0), new VisualPoint(1, 70, 0),
                new VisualPoint(2, 70, 0));
        for (int index = 0; index < points.size(); index++) {
            assertFalse(SettlementLayoutGeometry.elevationTransition(points, index),
                    "flat access must not become an alternating stair trench");
        }
        var rising = java.util.List.of(new VisualPoint(0, 70, 0), new VisualPoint(1, 70, 0),
                new VisualPoint(2, 71, 0));
        assertTrue(SettlementLayoutGeometry.elevationTransition(rising, 1));
        assertTrue(SettlementLayoutGeometry.elevationTransition(rising, 2));
    }

    @Test void perimeterSamplesAndRetainsAOneBlockRiseBetweenItsEndpoints() {
        var feature = new io.farfrontier.palemirror.api.LinearFeaturePlan("wall",
                LinearFeatureKind.PALISADE,
                java.util.List.of(new VisualPoint(0, 64, 0), new VisualPoint(8, 64, 0)), 2, false);
        var snapshot = new SettlementTerrainSnapshot(new VisualPoint(4, 65, 0),
                java.util.Map.of(SettlementTerrainSnapshot.key(0, 0), 65,
                        SettlementTerrainSnapshot.key(8, 0), 65),
                (x, z) -> x == 4 ? 66 : 65, (x, z) -> false);

        var followed = SettlementLayoutGeometry.followExactTerrain(feature, snapshot);
        assertEquals(9, followed.nodes().size());
        assertEquals(65, followed.nodes().get(4).y(), "wall datum must follow the hidden one-block hill");
        assertEquals(java.util.List.of(0, 1, 2, 3, 4, 5, 6, 7, 8),
                followed.nodes().stream().map(VisualPoint::x).toList(),
                "the perimeter contract must retain every physical wall column for structural compilation");
    }

    @Test void everyFacadeGetsAGroundedObstacleFreeRouteToThePublicGraph() {
        var plan = plan(7);
        for (int index = 0; index < plan.buildings().size(); index++) {
            var owner = plan.buildings().get(index);
            String accessId = "access_" + index;
            var access = plan.circulation().stream().filter(value -> value.id().equals(accessId))
                    .findFirst().orElseThrow();
            VisualPoint entrance = SettlementLayoutGeometry.publicEntrance(owner);
            assertEquals(new VisualPoint(entrance.x(), entrance.y() - 1, entrance.z()),
                    access.nodes().getFirst(), "access must start on the exact authored threshold");
            for (VisualPoint point : raster(access)) {
                plan.buildings().stream().filter(value -> value != owner).forEach(other -> assertFalse(
                        containsHorizontal(other.modules().getFirst().footprint(), point),
                        owner.buildingId() + " access crossed " + other.buildingId() + " at " + point));
            }
        }
    }

    @Test void everyPublicPortUsesTheCuratedNbtThresholdAfterRotation() {
        var plan = plan(7);
        plan.modules().forEach(module -> {
            String template = module.templateId().substring(module.templateId().lastIndexOf('/') + 1);
            var definition = FrontierModuleCatalog.require(template);
            var expected = SettlementLayoutGeometry.authoredEntrance(definition, module.footprint(),
                    module.quarterTurns());
            var actual = module.ports().stream().filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                    .findFirst().orElseThrow().position();
            assertEquals(expected, actual, module.instanceId() + " regressed to a geometric facade guess");
        });
    }

    private static int horizontalLength(io.farfrontier.palemirror.api.LinearFeaturePlan feature) {
        int length = 0;
        for (int index = 1; index < feature.nodes().size(); index++) {
            var first = feature.nodes().get(index - 1);
            var second = feature.nodes().get(index);
            length += Math.max(Math.abs(second.x() - first.x()), Math.abs(second.z() - first.z()));
        }
        return length;
    }

    private static boolean crosses(io.farfrontier.palemirror.api.LinearFeaturePlan feature,
                                   VisualPoint point) {
        for (int index = 1; index < feature.nodes().size(); index++) {
            VisualPoint first = feature.nodes().get(index - 1);
            VisualPoint second = feature.nodes().get(index);
            if (point.x() >= Math.min(first.x(), second.x()) && point.x() <= Math.max(first.x(), second.x())
                    && point.z() >= Math.min(first.z(), second.z())
                    && point.z() <= Math.max(first.z(), second.z())) return true;
        }
        return false;
    }

    private static java.util.List<VisualPoint> raster(
            io.farfrontier.palemirror.api.LinearFeaturePlan feature) {
        java.util.List<VisualPoint> result = new java.util.ArrayList<>();
        for (int segment = 1; segment < feature.nodes().size(); segment++) {
            VisualPoint from = feature.nodes().get(segment - 1);
            VisualPoint to = feature.nodes().get(segment);
            int steps = Math.max(1, Math.max(Math.abs(to.x() - from.x()), Math.abs(to.z() - from.z())));
            for (int step = segment == 1 ? 0 : 1; step <= steps; step++) result.add(new VisualPoint(
                    from.x() + (to.x() - from.x()) * step / steps,
                    from.y() + (to.y() - from.y()) * step / steps,
                    from.z() + (to.z() - from.z()) * step / steps));
        }
        return result;
    }

    private static boolean containsHorizontal(io.farfrontier.palemirror.api.VisualBounds bounds,
                                              VisualPoint point) {
        return point.x() >= bounds.min().x() && point.x() <= bounds.max().x()
                && point.z() >= bounds.min().z() && point.z() <= bounds.max().z();
    }

    @Test void twentyAddressesRetainStableIndependentModuleIdentity() {
        var identities = new HashSet<String>();
        for (int index = 0; index < 20; index++) {
            VisualPoint point = new VisualPoint(index * 2_000, 70 + index % 5, index * -1_500);
            var plan = planner.plan("region_" + index, point, FrontierClimate.values()[index % 3], index % 4,
                    new TerrainCandidate(point, 2 + index % 14, 0, 0, index, index % 3, index % 5));
            assertTrue(identities.add(plan.layoutId()));
            assertEquals(20, plan.modules().stream().map(value -> value.instanceId()).distinct().count());
        }
    }

    @Test void everyBoundedTerrainVariantPreservesTheWholeMasterplanContract() {
        for (int relief : new int[]{2, 7, 14}) for (int direction = 0; direction < 4; direction++) {
            TerrainCandidate terrain = new TerrainCandidate(anchor, relief, 0, 0, relief, direction, 0);
            for (int variant = 0; variant < SettlementLayoutPlanner.VARIANT_COUNT; variant++) {
                var plan = planner.planKnownVariant("variant", anchor, FrontierClimate.TEMPERATE, direction,
                        terrain, SettlementTerrainSnapshot.flat(anchor), variant);
                assertEquals(20, plan.buildings().size());
                assertEquals(6, plan.openSpaces().size());
                assertEquals(7, plan.developmentReservations().size());
                var depot = plan.buildings().stream()
                        .filter(value -> value.buildingId().equals("receiving_depot"))
                        .findFirst().orElseThrow().modules().getFirst();
                var publicEntrance = depot.ports().stream()
                        .filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                        .findFirst().orElseThrow().position();
                var freight = depot.ports().stream().filter(value -> value.kind() == VisualPortKind.FREIGHT)
                        .findFirst().orElseThrow().position();
                var rail = depot.ports().stream().filter(value -> value.kind() == VisualPortKind.RAIL)
                        .findFirst().orElseThrow().position();
                assertEquals(freight, rail, "freight capability and canonical railhead must share an endpoint");
                assertFalse(publicEntrance.equals(rail),
                        "passenger threshold and freight railhead must remain physically distinct");
                assertEquals(rail, plan.receivingDepot());
                assertEquals(depot.origin(), plan.depotFunctionalCore());
                assertFalse(plan.depotFunctionalCore().equals(plan.receivingRailhead()),
                        "depot functional core and railway hand-off must remain distinct");
            }
        }
    }

    @Test void exactTerrainFailureRejectsTheWholeCenterInsteadOfFlatteningIt() {
        java.util.Map<Long, Integer> coarse = new java.util.LinkedHashMap<>();
        for (int x = anchor.x() - SettlementLayoutPlanner.MASTER_HALF_WIDTH;
             x <= anchor.x() + SettlementLayoutPlanner.MASTER_HALF_WIDTH; x += SettlementTerrainSnapshot.GRID_STEP) {
            for (int z = anchor.z() - SettlementLayoutPlanner.MASTER_HALF_LENGTH;
                 z <= anchor.z() + SettlementLayoutPlanner.MASTER_HALF_LENGTH;
                 z += SettlementTerrainSnapshot.GRID_STEP) {
                coarse.put(SettlementTerrainSnapshot.key(x, z), anchor.y());
            }
        }
        SettlementTerrainSnapshot cliff = new SettlementTerrainSnapshot(anchor, coarse,
                (x, z) -> x >= anchor.x() ? anchor.y() + 12 : anchor.y(), (x, z) -> false);
        org.junit.jupiter.api.Assertions.assertThrows(DryMineSiteUnavailableException.class, () -> planner.plan(
                "cliff", anchor, FrontierClimate.TEMPERATE, 0,
                new TerrainCandidate(anchor, 2, 0, 0, 0), cliff));
    }

    @Test void repeatedPadAndRoadChecksSpendOnlyUniqueExactColumns() {
        java.util.concurrent.atomic.AtomicInteger heights = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger waters = new java.util.concurrent.atomic.AtomicInteger();
        SettlementTerrainSnapshot snapshot = new SettlementTerrainSnapshot(anchor,
                java.util.Map.of(SettlementTerrainSnapshot.key(anchor.x(), anchor.z()), anchor.y()),
                (x, z) -> { heights.incrementAndGet(); return anchor.y(); },
                (x, z) -> { waters.incrementAndGet(); return false; });
        var footprint = new io.farfrontier.palemirror.api.VisualBounds(
                new VisualPoint(anchor.x() - 4, anchor.y(), anchor.z() - 4),
                new VisualPoint(anchor.x() + 4, anchor.y() + 6, anchor.z() + 4));

        assertTrue(snapshot.resolvePad(footprint).accepted());
        assertTrue(snapshot.resolvePad(footprint).accepted());
        snapshot.waterAt(anchor.x(), anchor.z());

        assertEquals(9, snapshot.exactProbes());
        assertEquals(9, heights.get());
        assertEquals(9, waters.get());
    }

    @Test void equidistantCoarseHeightsUseAStableCoordinateTieBreak() {
        long west = SettlementTerrainSnapshot.key(0, 0);
        long east = SettlementTerrainSnapshot.key(16, 0);
        var westFirst = new java.util.LinkedHashMap<Long, Integer>();
        westFirst.put(west, 64);
        westFirst.put(east, 80);
        var eastFirst = new java.util.LinkedHashMap<Long, Integer>();
        eastFirst.put(east, 80);
        eastFirst.put(west, 64);

        SettlementTerrainSnapshot first = new SettlementTerrainSnapshot(new VisualPoint(8, 70, 0), westFirst,
                (x, z) -> 70, (x, z) -> false);
        SettlementTerrainSnapshot second = new SettlementTerrainSnapshot(new VisualPoint(8, 70, 0), eastFirst,
                (x, z) -> 70, (x, z) -> false);

        assertEquals(64, first.approximateHeight(8, 0));
        assertEquals(first.approximateHeight(8, 0), second.approximateHeight(8, 0));
    }

    @Test void terrainFollowingRoadsExpandOnlyTheVerticalSiteIndex() {
        var coarse = new java.util.LinkedHashMap<Long, Integer>();
        for (int right = -SettlementLayoutPlanner.MASTER_HALF_WIDTH;
             right <= SettlementLayoutPlanner.MASTER_HALF_WIDTH; right += SettlementTerrainSnapshot.GRID_STEP) {
            for (int inward = -SettlementLayoutPlanner.MASTER_HALF_LENGTH;
                 inward <= SettlementLayoutPlanner.MASTER_HALF_LENGTH; inward += SettlementTerrainSnapshot.GRID_STEP) {
                coarse.put(SettlementTerrainSnapshot.key(anchor.x() + inward, anchor.z() + right), anchor.y());
            }
        }
        coarse.put(SettlementTerrainSnapshot.key(anchor.x() - 92, anchor.z() - 68), anchor.y() - 12);
        SettlementTerrainSnapshot shelf = new SettlementTerrainSnapshot(anchor, coarse,
                (x, z) -> x == anchor.x() - 92 && z == anchor.z() - 68
                        ? anchor.y() - 12 : anchor.y(), (x, z) -> false);
        var plan = planner.plan("shelf", anchor, FrontierClimate.TEMPERATE, 0,
                new TerrainCandidate(anchor, 2, 0, 0, 0), shelf);
        assertEquals(2 * SettlementLayoutPlanner.MASTER_HALF_LENGTH,
                plan.bounds().max().x() - plan.bounds().min().x());
        assertEquals(2 * SettlementLayoutPlanner.MASTER_HALF_WIDTH,
                plan.bounds().max().z() - plan.bounds().min().z());
        assertTrue(plan.bounds().min().y() <= anchor.y() - 12);
        plan.circulation().stream().flatMap(value -> value.nodes().stream())
                .forEach(point -> assertTrue(plan.bounds().contains(point)));
    }

    private io.farfrontier.palemirror.api.AuthoredSettlementSitePlan plan(int relief) {
        return planner.plan("test", anchor, FrontierClimate.TEMPERATE, 0,
                new TerrainCandidate(anchor, relief, 0, 0, relief, relief, 0));
    }

    private static boolean overlaps(io.farfrontier.palemirror.api.VisualBounds a,
                                    io.farfrontier.palemirror.api.VisualBounds b) {
        return a.min().x() <= b.max().x() && a.max().x() >= b.min().x()
                && a.min().z() <= b.max().z() && a.max().z() >= b.min().z();
    }

    private static boolean contains(io.farfrontier.palemirror.api.VisualBounds outer,
                                    io.farfrontier.palemirror.api.VisualBounds inner) {
        return outer.contains(inner.min()) && outer.contains(inner.max());
    }
}
