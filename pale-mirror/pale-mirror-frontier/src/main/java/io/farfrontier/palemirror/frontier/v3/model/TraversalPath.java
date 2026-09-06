package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;
import java.util.Objects;

/**
 * Bounded immutable pedestrian/bioform path over semantic support surfaces.
 *
 * <p>This initial topology primitive models ordinary one-cell walks and one-block grades.  It
 * deliberately rejects zero-length, diagonal and vertical-only jumps; ladders, lifts and rail
 * ramps will be separate typed edge kinds rather than accidental extensions of a flat corridor.</p>
 */
public record TraversalPath(List<SurfaceAnchor> surfaces) {
    public TraversalPath {
        surfaces = List.copyOf(Objects.requireNonNull(surfaces, "traversal surfaces"));
        if (surfaces.isEmpty() || surfaces.size() > 256) {
            throw new IllegalArgumentException("traversal path must contain 1..256 surfaces");
        }
        for (int index = 0; index < surfaces.size(); index++) {
            SurfaceAnchor current = Objects.requireNonNull(surfaces.get(index), "traversal surface");
            if (index == 0) continue;
            SurfaceAnchor previous = surfaces.get(index - 1);
            int horizontal = Math.abs(current.x() - previous.x()) + Math.abs(current.z() - previous.z());
            if (horizontal != 1 || Math.abs(current.y() - previous.y()) > 1) {
                throw new IllegalArgumentException("traversal path must use adjacent surfaces with grade at most one");
            }
        }
    }

    public SurfaceAnchor first() { return surfaces.getFirst(); }
    public SurfaceAnchor last() { return surfaces.getLast(); }
}
