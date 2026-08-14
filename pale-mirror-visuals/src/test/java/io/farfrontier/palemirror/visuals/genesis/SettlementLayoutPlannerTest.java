package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.LinearFeatureKind;
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
                    || feature.kind() == LinearFeatureKind.DITCH || feature.kind() == LinearFeatureKind.RETAINING_WALL));
            assertTrue(plan.defences().stream().noneMatch(feature -> feature.nodes().contains(plan.freightGate())));
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
            assertFalse(plan.managedArea().contains(plan.bounds().min().x(), plan.bounds().min().z()),
                    "the managed settlement edge must remain irregular rather than claim the master rectangle");
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
        }
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

    @Test void terrainFollowingRoadsExpandOnlyTheVerticalSiteIndex() {
        var coarse = new java.util.LinkedHashMap<Long, Integer>();
        for (int right = -SettlementLayoutPlanner.MASTER_HALF_WIDTH;
             right <= SettlementLayoutPlanner.MASTER_HALF_WIDTH; right += SettlementTerrainSnapshot.GRID_STEP) {
            for (int inward = -SettlementLayoutPlanner.MASTER_HALF_LENGTH;
                 inward <= SettlementLayoutPlanner.MASTER_HALF_LENGTH; inward += SettlementTerrainSnapshot.GRID_STEP) {
                coarse.put(SettlementTerrainSnapshot.key(anchor.x() + inward, anchor.z() + right), anchor.y());
            }
        }
        coarse.put(SettlementTerrainSnapshot.key(anchor.x() - 87, anchor.z() - 59), anchor.y() - 12);
        SettlementTerrainSnapshot shelf = new SettlementTerrainSnapshot(anchor, coarse,
                (x, z) -> anchor.y(), (x, z) -> false);
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
