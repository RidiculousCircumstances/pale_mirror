package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.*;
import java.util.*;
import java.util.function.Predicate;

/** Conservative local graph preservation, not route discovery or a terrain writer. */
public final class WorkingSurfaceTransition {
    private WorkingSurfaceTransition() { }
    public static boolean preservesAccess(SurfaceAnchor replacement, Map<TerrainColumn, SurfaceAnchor> current,
                                         Predicate<SurfaceAnchor> open, Set<BlockPosition> occupiedSupports) {
        var prior = current.get(new TerrainColumn(replacement.x(), replacement.z()));
        if (prior == null) return true;
        if (replacement.y() != prior.y() - 1 || occupiedSupports.contains(prior.support())) return false;
        for (int[] direction : List.of(new int[]{0, -1}, new int[]{-1, 0}, new int[]{1, 0}, new int[]{0, 1})) {
            var neighbour = current.get(new TerrainColumn(prior.x() + direction[0], prior.z() + direction[1]));
            if (neighbour != null && open.test(neighbour) && Math.abs(prior.y() - neighbour.y()) <= 1
                    && Math.abs(replacement.y() - neighbour.y()) > 1) return false;
        }
        return true;
    }
}
