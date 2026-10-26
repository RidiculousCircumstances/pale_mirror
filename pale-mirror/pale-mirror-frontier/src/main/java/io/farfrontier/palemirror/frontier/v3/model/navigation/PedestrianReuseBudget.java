package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

/** Bounds optional cached detours, never the ordinary search or actual terrain connectivity.
 * A distant trunk is useful only while its extra travel remains proportionate to this query. */
final class PedestrianReuseBudget {
    private PedestrianReuseBudget() { }
    static long cells(SurfaceAnchor start, SurfaceAnchor target) {
        return Math.max(128, 4 * (Math.abs((long) start.x() - target.x())
                + Math.abs((long) start.z() - target.z()) + Math.abs((long) start.y() - target.y())));
    }
    static long portals(SurfaceAnchor start, SurfaceAnchor target) {
        var from = PedestrianRegion.Tile.at(start); var to = PedestrianRegion.Tile.at(target);
        return Math.max(16, 4 * (Math.abs((long) from.x() - to.x()) + Math.abs((long) from.z() - to.z())));
    }
}
