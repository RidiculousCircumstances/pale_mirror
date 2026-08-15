package io.farfrontier.palemirror.visuals.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.farfrontier.palemirror.api.VisualBounds;
import io.farfrontier.palemirror.api.VisualPoint;
import io.farfrontier.palemirror.api.VisualPortKind;

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
