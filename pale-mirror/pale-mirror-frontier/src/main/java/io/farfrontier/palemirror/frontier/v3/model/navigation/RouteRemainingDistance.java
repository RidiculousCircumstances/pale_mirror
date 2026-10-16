package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.OptionalLong;

/** Read-only projection onto compressed accepted geometry, without expanding or searching the world. */
public final class RouteRemainingDistance {
    private RouteRemainingDistance() { }
    public static OptionalLong edges(PedestrianPathProof path, SurfaceAnchor position) {
        var start = path.origin(); long traversed = 0;
        // First occurrence is conservative on loops; off-route positions are not a straight-line shortcut.
        for (var run : path.runs()) {
            long dx = (long) position.x() - start.x(), dy = (long) position.y() - start.y(), dz = (long) position.z() - start.z();
            long offset = run.dx() != 0 ? dx / run.dx() : run.dz() != 0 ? dz / run.dz() : dy / run.dy();
            if (offset >= 0 && offset <= run.edges() && dx == offset * run.dx()
                    && dy == offset * run.dy() && dz == offset * run.dz())
                return OptionalLong.of(path.edges() - traversed - offset);
            start = SurfaceAnchor.at(Math.addExact(start.x(), Math.multiplyExact(run.dx(), run.edges())),
                    Math.addExact(start.y(), Math.multiplyExact(run.dy(), run.edges())),
                    Math.addExact(start.z(), Math.multiplyExact(run.dz(), run.edges())));
            traversed += run.edges();
        }
        return start.equals(position) ? OptionalLong.of(0) : OptionalLong.empty();
    }
}
