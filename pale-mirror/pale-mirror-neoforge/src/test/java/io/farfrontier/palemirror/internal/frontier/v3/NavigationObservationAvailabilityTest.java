package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.geometry.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NavigationObservationAvailabilityTest {
    @Test void unloadedNeighbourDoesNotEraseKnownColdGeometryButUnsupportedLoadedShapeDoes() {
        var missing = new BlockPosition(-97, 63, 0);
        var unsupported = new BlockPosition(-96, 63, 0);
        var before = NavigationGeometryState.empty().observe(0, Map.of(missing, Optional.of(NavigationCell.SOLID)));
        var samples = FrontierV3NavigationObservation.captureAvailable(List.of(missing, unsupported),
                p -> !p.equals(missing), p -> { assertEquals(unsupported, p); return Optional.empty(); });
        var after = before.observe(1, samples);
        assertEquals(Optional.of(NavigationCell.SOLID), after.cells().get(missing));
        assertEquals(Optional.empty(), after.cells().get(unsupported));
        var loaded = FrontierV3NavigationObservation.captureAvailable(List.of(missing), p -> true,
                p -> Optional.of(NavigationCell.AIR));
        assertEquals(Optional.of(NavigationCell.AIR), after.observe(2, loaded).cells().get(missing));
    }
}
