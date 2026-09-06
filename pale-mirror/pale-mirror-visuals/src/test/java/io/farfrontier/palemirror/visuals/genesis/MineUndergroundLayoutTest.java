package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPortKind;
import io.farfrontier.palemirror.api.AuthoredMineSitePlan;
import io.farfrontier.palemirror.api.MineFoundationPlan;

import org.junit.jupiter.api.Test;

class MineUndergroundLayoutTest {
    @Test void compactGrammarHasOneTrunkAndTwoDistinctBranches() {
        var corridors = MineUndergroundLayout.corridors();

        assertEquals(3, corridors.size());
        assertEquals(MineUndergroundLayout.PORTAL, corridors.getFirst().getFirst());
        assertEquals(MineUndergroundLayout.JUNCTION, corridors.getFirst().getLast());
        assertEquals(MineUndergroundLayout.JUNCTION, corridors.get(1).getFirst());
        assertEquals(MineUndergroundLayout.JUNCTION, corridors.get(2).getFirst());
        assertEquals(MineUndergroundLayout.GALLERY, corridors.get(1).getLast());
        assertEquals(MineUndergroundLayout.CONTROLLER, corridors.get(2).getLast());
        assertTrue(MineUndergroundLayout.GALLERY.right() > MineUndergroundLayout.JUNCTION.right());
        assertTrue(MineUndergroundLayout.CONTROLLER.right() < MineUndergroundLayout.JUNCTION.right());
        assertTrue(corridors.stream().flatMap(java.util.Collection::stream)
                .allMatch(node -> node.inward() <= 34 && node.up() >= -12));
    }

    @Test void surfaceAccessStopsOutsideEveryBuildingAndUsesWalkableGrades() {
        var mine = new FrontierRegionPlanner().plan(621L, 0, new VisualPoint(0, 72, 0),
                FrontierClimate.TEMPERATE).primaryMineSite();
        var routes = MineAccessGenesisCompiler.routes(mine);

        assertEquals(mine.foundations().size(), routes.size());
        for (var route : routes) {
            var ownerModules = mine.surfaceBuildings().stream().flatMap(value -> value.modules().stream())
                    .filter(value -> value.foundationId().equals(route.foundationId())).toList();
            for (VisualPoint point : route.points()) {
                assertTrue(mine.surfaceBuildings().stream().flatMap(value -> value.modules().stream())
                                .filter(value -> !ownerModules.contains(value))
                                .noneMatch(value -> contains(value.footprint(), point)),
                        route.foundationId() + " entered another building at " + point);
            }
            for (int index = 1; index < route.points().size(); index++) {
                VisualPoint previous = route.points().get(index - 1);
                VisualPoint current = route.points().get(index);
                assertEquals(1, Math.abs(previous.x() - current.x()) + Math.abs(previous.z() - current.z()));
                assertTrue(Math.abs(previous.y() - current.y()) <= 1,
                        route.foundationId() + " emitted an unwalkable road step");
            }
            if (route.minePortal()) {
                VisualPoint expected = outside(mine.portal(), Math.floorMod(mine.inwardQuarterTurns() + 2, 4));
                VisualPoint actual = route.points().getLast();
                assertEquals(expected.x(), actual.x(), "main mine road must meet the real adit x");
                assertEquals(expected.z(), actual.z(), "main mine road must meet the real adit z");
                assertEquals(mine.portal().y() - 1, actual.y(), "main mine road must meet the adit floor");
                continue;
            }
            if (!ownerModules.isEmpty()) {
                var port = ownerModules.stream().flatMap(value -> value.ports().stream())
                        .filter(value -> value.kind() == VisualPortKind.PUBLIC_ENTRANCE)
                        .findFirst().orElseThrow();
                VisualPoint expected = outside(port.position(), port.outwardQuarterTurns());
                VisualPoint actual = route.points().getLast();
                assertEquals(expected.x(), actual.x(), route.foundationId() + " access x");
                assertEquals(expected.z(), actual.z(), route.foundationId() + " access z");
                assertTrue(Math.abs(expected.y() - actual.y()) <= 2,
                        route.foundationId() + " access does not meet the authored threshold grade");
            }
        }
        assertEquals(1L, routes.stream().filter(MineAccessGenesisCompiler.AccessRoute::minePortal).count());
    }

    @Test void surfaceAccessCompilesAcrossDeterministicLayoutsAndOrientations() {
        for (int index = 0; index < 128; index++) {
            var seed = new FrontierRegionPlanner().plan(1204015150661999743L + index * 7919L, index,
                    new VisualPoint(index * 193, 64 + index % 27, -index * 211),
                    FrontierClimate.values()[index % FrontierClimate.values().length]);
            for (var mine : java.util.List.of(seed.primaryMineSite(), seed.alternateMineSite())) {
                var routes = MineAccessGenesisCompiler.routes(mine);
                assertEquals(MineSurfaceLayout.materializedFoundationIds(mine).size(), routes.size());
                assertEquals(1L, routes.stream().filter(MineAccessGenesisCompiler.AccessRoute::minePortal).count());
            }
        }
    }

    @Test void portalApproachEscapesTheActualAuthoredFoundation() {
        var original = new FrontierRegionPlanner().plan(621L, 0, new VisualPoint(0, 72, 0),
                FrontierClimate.TEMPERATE).primaryMineSite();
        int outward = Math.floorMod(original.inwardQuarterTurns() + 2, 4);
        var foundations = original.foundations().stream().map(foundation -> {
            if (!foundation.id().equals("portal")) return foundation;
            VisualBounds bounds = foundation.footprint();
            VisualPoint min = bounds.min();
            VisualPoint max = bounds.max();
            VisualBounds widened = switch (outward) {
                case 0 -> new VisualBounds(min, new VisualPoint(max.x() + 8, max.y(), max.z()));
                case 1 -> new VisualBounds(min, new VisualPoint(max.x(), max.y(), max.z() + 8));
                case 2 -> new VisualBounds(new VisualPoint(min.x() - 8, min.y(), min.z()), max);
                default -> new VisualBounds(new VisualPoint(min.x(), min.y(), min.z() - 8), max);
            };
            return new MineFoundationPlan(foundation.id(), widened, foundation.targetY(), foundation.apron(),
                    foundation.maximumCut(), foundation.maximumFill());
        }).toList();
        var mine = new AuthoredMineSitePlan(original.siteId(), original.role(), original.portal(),
                original.loadingEndpoint(), original.controllerAnchor(), original.bounds(),
                original.inwardQuarterTurns(), original.surfaceBuildings(), original.undergroundModules(),
                original.stagedModules(), foundations, original.environment(), original.semanticVolumes());

        var portalRoute = MineAccessGenesisCompiler.routes(mine).stream()
                .filter(MineAccessGenesisCompiler.AccessRoute::minePortal).findFirst().orElseThrow();

        VisualPoint expected = outside(mine.portal(), outward);
        assertEquals(expected.x(), portalRoute.points().getLast().x());
        assertEquals(expected.z(), portalRoute.points().getLast().z());
    }

    private static boolean contains(VisualBounds bounds, VisualPoint point) {
        return point.x() >= bounds.min().x() && point.x() <= bounds.max().x()
                && point.z() >= bounds.min().z() && point.z() <= bounds.max().z();
    }

    private static VisualPoint outside(VisualPoint entrance, int outward) {
        return switch (Math.floorMod(outward, 4)) {
            case 0 -> new VisualPoint(entrance.x() + 1, entrance.y(), entrance.z());
            case 1 -> new VisualPoint(entrance.x(), entrance.y(), entrance.z() + 1);
            case 2 -> new VisualPoint(entrance.x() - 1, entrance.y(), entrance.z());
            default -> new VisualPoint(entrance.x(), entrance.y(), entrance.z() - 1);
        };
    }
}
