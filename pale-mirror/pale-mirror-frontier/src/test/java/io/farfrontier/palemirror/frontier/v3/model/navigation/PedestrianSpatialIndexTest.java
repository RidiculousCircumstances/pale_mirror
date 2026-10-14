package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PedestrianSpatialIndexTest {
    @Test void discoveryCoversIntermediateTravelChunksAndRetiresOldSegmentsWithoutProjectingTheWholePopulation() {
        var index = new PedestrianSpatialIndex(); var walker = new SubjectId("actor:walker");
        var route = java.util.stream.IntStream.rangeClosed(-512, 512).mapToObj(x -> SurfaceAnchor.at(x, 63, 0)).toList();
        index.replace(walker, BodyPosition.above(route.getFirst()), route);
        for (int i = 0; i < 6000; i++) index.replace(new SubjectId("actor:distant-" + i),
                BodyPosition.above(SurfaceAnchor.at(10000 + i, 63, 10000)), List.of());
        assertEquals(List.of(walker), index.candidates(0, 0, 32));
        assertEquals(List.of(walker), index.candidates(-1, -1, 1), "negative chunk coordinates and boundary contact remain discoverable");
        index.replace(walker, BodyPosition.above(route.getLast()), List.of());
        assertTrue(index.candidates(0, 0, 32).isEmpty());
        assertEquals(List.of(walker), index.candidates(512, 0, 0));
        index.remove(walker); assertTrue(index.candidates(512, 0, 0).isEmpty());
    }
}
