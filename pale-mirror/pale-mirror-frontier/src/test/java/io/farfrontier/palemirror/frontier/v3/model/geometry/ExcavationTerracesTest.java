package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ExcavationTerracesTest {
    @Test void deeperSelectionNeedsAnOpenBenchAndCannotUndermineRetainedColumnsOrExceedDepth() {
        var policy = new ExcavationTerraces(2, 4);
        var bench = java.util.stream.IntStream.range(0, 4).mapToObj(x -> SurfaceAnchor.at(x, 0, 0)).toList();
        var sample = new KnownBlockGeometry.Sample<>("rock", 1, KnownBlockGeometry.Sample.Permission.PUBLIC);
        var proposed = AdjacentExcavationPlanner.propose(bench, p -> Optional.of(sample), p -> true, p -> true,
                "rock"::equals, "rock"::equals, 8, policy.selection(0, bench, p -> true));
        assertTrue(proposed.stream().anyMatch(p -> p.station().equals(bench.getFirst()) && p.floor().y() == -1));
        assertTrue(proposed.stream().noneMatch(p -> bench.stream().anyMatch(old -> old.x() == p.floor().x() && old.z() == p.floor().z())));
        assertEquals(List.of(0), policy.selection(0, bench, p -> !p.equals(bench.get(2))).floorOffsets(bench.getFirst(), -1, 0));
        var landing = SurfaceAnchor.at(-1, -1, 0);
        var withLanding = new ArrayList<>(bench); withLanding.add(landing);
        assertEquals(List.of(0), policy.selection(0, withLanding, p -> true).floorOffsets(landing, -1, 0),
                "a single step is not another horizontal bench");
        var bottom = java.util.stream.IntStream.range(0, 4).mapToObj(x -> SurfaceAnchor.at(x, -2, 0)).toList();
        assertEquals(List.of(0), policy.selection(0, bottom, p -> true).floorOffsets(bottom.getFirst(), -1, 0));
        assertThrows(IllegalArgumentException.class, () -> policy.selection(0, List.of(landing, SurfaceAnchor.at(-1, -2, 0)), p -> true));
        assertThrows(IllegalArgumentException.class, () -> new ExcavationTerraces(0, 4));
        assertThrows(IllegalArgumentException.class, () -> new ExcavationTerraces(2, 1));
    }
    @Test void exteriorColumnsRequireTheirWholeKnownPublicClearanceAndRespectPhysicalReach() {
        var bench = java.util.stream.IntStream.range(0, 4).mapToObj(x -> SurfaceAnchor.at(x, 0, 0)).toList();
        var sample = new KnownBlockGeometry.Sample<>("rock", 1, KnownBlockGeometry.Sample.Permission.PUBLIC);
        var strategy = new ExcavationTerraces(2, 4).selection(0, OptionalInt.of(3), bench, p -> true);
        var columns = AdjacentExcavationPlanner.propose(bench, p -> Optional.of(sample), p -> true, p -> true,
                "rock"::equals, "rock"::equals, 8, strategy);
        assertTrue(columns.stream().allMatch(column -> column.floor().y() + column.clearance().size() == 3));
        assertTrue(AdjacentExcavationPlanner.propose(bench, p -> p.y() == 3 ? Optional.empty() : Optional.of(sample),
                p -> true, p -> true, "rock"::equals, "rock"::equals, 8, strategy).isEmpty());
        var protectedRoof = new KnownBlockGeometry.Sample<>("rock", 1, KnownBlockGeometry.Sample.Permission.PROTECTED);
        assertTrue(AdjacentExcavationPlanner.propose(bench, p -> Optional.of(p.y() == 3 ? protectedRoof : sample),
                p -> true, p -> true, "rock"::equals, "rock"::equals, 8, strategy).isEmpty());
        var reach = BlockInteractionReach.VANILLA_ADULT_PEDESTRIAN;
        assertTrue(reach.contains(SurfaceAnchor.at(0, 57, 0), new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(1, 63, 0)));
        assertFalse(reach.contains(SurfaceAnchor.at(0, 56, 0), new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(1, 63, 0)));
        assertFalse(reach.contains(SurfaceAnchor.at(0, 57, 0), new io.farfrontier.palemirror.frontier.v3.model.BlockPosition(1, 52, 0)));
    }
}
