package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteGeometry;
import java.util.*;

/** Common multi-level work access candidates. Does not select work, reserve space or calculate a route. */
public final class BlockWorkAccess {
    private BlockWorkAccess() { }
    public static List<SurfaceAnchor> candidates(PedestrianRouteGeometry geometry, BlockPosition target, BlockInteractionReach reach) {
        var result = new ArrayList<SurfaceAnchor>(); int radius = Math.ceilDiv(reach.radiusMilli(), 1000);
        for (int x = target.x() - radius; x <= target.x() + radius; x++)
            for (int z = target.z() - radius; z <= target.z() + radius; z++) {
                if (!geometry.bounds().contains(new BlockPosition(x, target.y(), z))) continue;
                for (var surface : geometry.surfacesAt(x, z))
                    if (reach.contains(surface, target) && geometry.clearanceKnown(surface) && geometry.clearStation(surface)) result.add(surface);
            }
        return List.copyOf(result);
    }
}
