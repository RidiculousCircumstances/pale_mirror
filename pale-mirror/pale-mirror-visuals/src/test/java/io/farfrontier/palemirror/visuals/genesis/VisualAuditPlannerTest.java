package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.VisualAuditView;
import io.farfrontier.palemirror.api.VisualPoint;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class VisualAuditPlannerTest {
    private static final VisualPoint ANCHOR = new VisualPoint(8_000, 72, -4_000);

    @Test void everyClimateAndArchetypeHasTheCompleteSemanticCameraGrammar() {
        for (FrontierClimate climate : FrontierClimate.values()) {
            for (int relief : new int[]{2, 7, 14}) {
                var region = new FrontierRegionPlanner().plan(9_182_733L + relief, climate.ordinal(),
                        new TerrainCandidate(ANCHOR, relief, 0, 0, relief), climate,
                        (requirement, candidates) -> new MountainMineAnchor(candidates.getFirst(), 0),
                        FrontierRegionPlanner::gradedManhattanRail);
                var views = VisualAuditPlanner.views(region);

                assertEquals(25, views.size(), climate + "/" + relief);
                assertEquals(views.size(), views.stream().map(VisualAuditView::id).distinct().count());
                assertEquals(Set.of("settlement", "mine", "railway"), views.stream()
                        .map(VisualAuditView::targetKind).collect(Collectors.toSet()));
                assertTrue(views.stream().allMatch(view -> view.dimensionId().equals("minecraft:overworld")));
                assertTrue(views.stream().allMatch(view -> Float.isFinite(view.yaw())
                        && Float.isFinite(view.pitch()) && view.pitch() >= -90.0F && view.pitch() <= 90.0F));
                assertContains(views, "settlement/market_square");
                assertContains(views, "settlement/residential_lane");
                assertContains(views, "primary_mine/industrial_campus");
                assertContains(views, "alternate_mine/portal");
                assertContains(views, "railway/corridor");
                VisualAuditView market = view(views, "settlement/market_square");
                var marketBounds = region.settlementSite().openSpaces().stream()
                        .filter(value -> value.kind() == io.farfrontier.palemirror.api.OpenSpaceKind.MARKET_SQUARE)
                        .findFirst().orElseThrow().bounds();
                assertFalse(horizontalContains(marketBounds, market.playerFeet()),
                        "open-space audit camera must stand outside its furniture footprint");
                VisualAuditView residential = view(views, "settlement/residential_lane");
                assertTrue(region.settlementSite().buildings().stream()
                        .noneMatch(value -> horizontalContains(value.parcel(), residential.playerFeet())),
                        "building camera must stand outside authored shells");
                VisualAuditView rail = view(views, "railway/corridor");
                assertTrue(region.baselineRailNodes().stream().noneMatch(value -> value.x() == rail.playerFeet().x()
                                && value.z() == rail.playerFeet().z()),
                        "railway camera must not stand on the track graph");
                VisualAuditView controller = view(views, "primary_mine/controller_chamber");
                assertTrue(controller.playerFeet().y() > region.primaryMineSite().controllerAnchor().y(),
                        "underground camera feet must stay above the module floor origin");
            }
        }
    }

    private static void assertContains(java.util.List<VisualAuditView> views, String id) {
        assertTrue(views.stream().anyMatch(view -> view.id().equals(id)), "missing semantic view " + id);
    }

    private static VisualAuditView view(java.util.List<VisualAuditView> views, String id) {
        return views.stream().filter(value -> value.id().equals(id)).findFirst().orElseThrow();
    }

    private static boolean horizontalContains(io.farfrontier.palemirror.api.VisualBounds bounds,
                                              VisualPoint point) {
        return point.x() >= bounds.min().x() && point.x() <= bounds.max().x()
                && point.z() >= bounds.min().z() && point.z() <= bounds.max().z();
    }
}
