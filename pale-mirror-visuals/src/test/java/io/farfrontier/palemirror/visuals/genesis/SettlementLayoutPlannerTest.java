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
            assertEquals(16, plan.modules().size());
            assertEquals(plan.modules().size(), plan.foundations().size());
            assertTrue(plan.bounds().max().x() - plan.bounds().min().x() <= 130);
            assertTrue(plan.bounds().max().z() - plan.bounds().min().z() <= 130);
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
            assertEquals(6, plan.expansionPlots().size());
            plan.expansionPlots().forEach(plot -> assertTrue(plan.modules().stream()
                    .noneMatch(module -> overlaps(plot, module.footprint())), "reserved plot overlaps a building"));
        }
    }

    @Test void twentyAddressesRetainStableIndependentModuleIdentity() {
        var identities = new HashSet<String>();
        for (int index = 0; index < 20; index++) {
            VisualPoint point = new VisualPoint(index * 2_000, 70 + index % 5, index * -1_500);
            var plan = planner.plan("region_" + index, point, FrontierClimate.values()[index % 3], index % 4,
                    new TerrainCandidate(point, 2 + index % 14, 0, 0, index, index % 3, index % 5));
            assertTrue(identities.add(plan.layoutId()));
            assertEquals(16, plan.modules().stream().map(value -> value.instanceId()).distinct().count());
        }
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
}
