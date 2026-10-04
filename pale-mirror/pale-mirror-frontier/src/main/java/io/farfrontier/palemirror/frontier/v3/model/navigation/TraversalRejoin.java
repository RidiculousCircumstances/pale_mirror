package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.List;
import java.util.Objects;

/** Owner-retained legal approach from an observed body to its next semantic checkpoint.
 * The approach is not an actor position store and does not award semantic arrival.
 */
public record TraversalRejoin(List<SurfaceAnchor> path, int cursor) {
    public static final int MAX_SURFACES = 4_096;
    public TraversalRejoin {
        path = List.copyOf(Objects.requireNonNull(path, "rejoin path"));
        if (path.isEmpty() || path.size() > MAX_SURFACES || cursor < 0 || cursor >= path.size()
                || path.stream().anyMatch(Objects::isNull))
            throw new IllegalArgumentException("rejoin requires a bounded known path and exact cursor");
        for (int i = 1; i < path.size(); i++) {
            var a = path.get(i - 1); var b = path.get(i);
            if (Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()) != 1 || Math.abs(a.y() - b.y()) > 1)
                throw new IllegalArgumentException("rejoin requires adjacent pedestrian surfaces");
        }
    }
    public SurfaceAnchor current() { return path.get(cursor); }
    public SurfaceAnchor target() { return path.getLast(); }
    public boolean arrived() { return cursor == path.size() - 1; }
    public int nextCursor(int maximumSteps) {
        if (maximumSteps < 1) throw new IllegalArgumentException("rejoin step budget must be positive");
        return Math.min(Math.addExact(cursor, maximumSteps), path.size() - 1);
    }
    public TraversalRejoin advance(int next, int maximumSteps) {
        if (next != nextCursor(maximumSteps))
            throw new IllegalArgumentException("rejoin progress must acknowledge its exact bounded successor");
        return new TraversalRejoin(path, next);
    }
}
