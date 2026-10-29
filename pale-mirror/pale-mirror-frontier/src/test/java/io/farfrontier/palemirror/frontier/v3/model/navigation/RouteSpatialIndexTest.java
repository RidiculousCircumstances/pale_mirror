package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouteSpatialIndexTest {
    @Test void verticalLevelsNodeMembershipAndCapturedInputRemainExact() {
        var route = new ArrayList<>(List.of(SurfaceAnchor.at(0, 63, 0), SurfaceAnchor.at(4, 63, 0),
                SurfaceAnchor.at(4, 65, 0), SurfaceAnchor.at(4, 65, 3)));
        var index = new RouteSpatialIndex(route); route.clear();
        assertEquals(2.0, index.projection(new ActorPositionView.TravelPoint(2.5, 64, 0.5)));
        assertEquals(5.0, index.projection(new ActorPositionView.TravelPoint(4.5, 65, 0.5)));
        assertEquals(7.5, index.projection(new ActorPositionView.TravelPoint(4.5, 66, 2.0)));
        assertTrue(index.containsNode(SurfaceAnchor.at(4, 65, 3)));
        assertTrue(index.containsNode(new ActorPositionView.TravelPoint(4.5, 66, 3.5)));
        assertFalse(index.containsNode(new ActorPositionView.TravelPoint(4.5, 65, 0.5)), "a segment interior is not a retained node");
        var single = new RouteSpatialIndex(List.of(SurfaceAnchor.at(-1, -1, 0)));
        assertEquals(0, single.projection(new ActorPositionView.TravelPoint(20, 50, 3)));
        assertTrue(single.containsNode(new ActorPositionView.TravelPoint(-0.5, 0.0, 0.5)));
        assertFalse(single.containsNode(new ActorPositionView.TravelPoint(-0.5, -0.0, 0.5)), "retain exact point equality");
        assertThrows(IllegalArgumentException.class, () -> new RouteSpatialIndex(List.of()));
    }
    @Test void indexedProjectionMatchesOriginalLinearGeometryOnFoldedNonFlatAndRepeatedRoutes() {
        var random = new Random(125);
        for (int example = 0; example < 16; example++) {
            var route = new ArrayList<SurfaceAnchor>();
            route.add(example % 3 == 0 ? SurfaceAnchor.at(2_000_000_000, 63, -2_000_000_000) : SurfaceAnchor.at(0, 63, 0));
            for (int i = 1; i < 160; i++) {
                var prior = route.getLast();
                route.add(i % 37 == 0 ? route.getFirst() : i % 29 == 0 ? prior
                        : SurfaceAnchor.at(prior.x() + random.nextInt(9) - 4, prior.y() + random.nextInt(3) - 1,
                                prior.z() + random.nextInt(9) - 4));
            }
            var index = new RouteSpatialIndex(route);
            assertEquals(0, index.projection(ActorPositionView.TravelPoint.at(route.getFirst().standingBody())),
                    "revisited nodes retain the earliest equal-distance segment");
            for (int query = 0; query < 64; query++) {
                var node = route.get(random.nextInt(route.size()));
                var point = query % 4 == 0 ? ActorPositionView.TravelPoint.at(node.standingBody())
                        : new ActorPositionView.TravelPoint(node.x() + random.nextInt(9) / 4.0,
                                node.y() + random.nextInt(9) / 5.0, node.z() + random.nextInt(9) / 4.0);
                assertEquals(linear(route, point), index.projection(point), 0.0);
                assertEquals(route.stream().anyMatch(surface -> point.equals(ActorPositionView.TravelPoint.at(surface.standingBody()))),
                        index.containsNode(point));
            }
        }
    }
    /** Retained pre-optimization oracle: checks geometric equivalence, not a timing budget. */
    private static double linear(List<SurfaceAnchor> route, ActorPositionView.TravelPoint position) {
        double best = 0, distance = Double.POSITIVE_INFINITY, travelled = 0;
        for (int index = 0; index < route.size() - 1; index++) {
            var a = route.get(index); var b = route.get(index + 1);
            double ax = a.x() + 0.5, ay = a.y() + 1.0, az = a.z() + 0.5;
            double dx = (double) b.x() - a.x(), dy = (double) b.y() - a.y(), dz = (double) b.z() - a.z();
            double lengthSquared = dx * dx + dy * dy + dz * dz;
            double fraction = lengthSquared == 0 ? 0 : Math.clamp(
                    ((position.x() - ax) * dx + (position.y() - ay) * dy + (position.z() - az) * dz) / lengthSquared, 0, 1);
            double x = ax + fraction * dx - position.x(), y = ay + fraction * dy - position.y(), z = az + fraction * dz - position.z();
            double candidate = x * x + y * y + z * z;
            double length = Math.sqrt(lengthSquared);
            if (candidate < distance) { best = travelled + fraction * length; distance = candidate; }
            travelled += length;
        }
        return best;
    }
}
