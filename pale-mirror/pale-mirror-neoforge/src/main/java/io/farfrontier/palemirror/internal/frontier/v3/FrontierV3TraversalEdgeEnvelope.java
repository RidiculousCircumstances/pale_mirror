package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;

/** Bounded physical interpolation envelope for one already-owned traversal edge. */
final class FrontierV3TraversalEdgeEnvelope {
    private FrontierV3TraversalEdgeEnvelope() { }

    static boolean contains(BodyPosition observed, SurfaceAnchor current, SurfaceAnchor next) {
        BodyPosition from = current.standingBody();
        BodyPosition to = next.standingBody();
        return observed.x() >= Math.min(from.x(), to.x()) && observed.x() <= Math.max(from.x(), to.x())
                && observed.y() >= Math.min(from.y(), to.y()) && observed.y() <= Math.max(from.y(), to.y())
                && observed.z() >= Math.min(from.z(), to.z()) && observed.z() <= Math.max(from.z(), to.z());
    }
}
