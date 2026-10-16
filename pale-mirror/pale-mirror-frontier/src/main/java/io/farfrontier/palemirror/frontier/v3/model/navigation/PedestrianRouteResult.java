package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.List;
import java.util.Objects;

/** Search readiness is not arrival. Resource/permit/activity outcomes belong to their owners. */
public record PedestrianRouteResult(Status status, List<SurfaceAnchor> route, long workUnits,
                                    int regions, String reason, java.util.Optional<PedestrianRouteEstimate> estimate,
                                    java.util.Set<io.farfrontier.palemirror.frontier.v3.model.BlockPosition> missingGeometry) {
    public enum Status {
        PLANNING(1), FOUND(2), NO_PATH(3), UNKNOWN_GEOMETRY(4), SAFETY_LIMIT(5), SEGMENT_READY(6), ESTIMATED(7);
        private final int wireTag;
        Status(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Status requireWireTag(int tag) {
            for (var value : values()) if (value.wireTag == tag) return value;
            throw new IllegalArgumentException("unknown pedestrian calculation status");
        }
    }
    public PedestrianRouteResult {
        Objects.requireNonNull(status); Objects.requireNonNull(reason); Objects.requireNonNull(estimate);
        route = List.copyOf(route);
        missingGeometry = java.util.Set.copyOf(missingGeometry);
        if (missingGeometry.size() > 512 || status != Status.UNKNOWN_GEOMETRY && !missingGeometry.isEmpty())
            throw new IllegalArgumentException("invalid navigation knowledge demand");
        if (workUnits < 0 || regions < 0 || (status == Status.FOUND || status == Status.SEGMENT_READY) != !route.isEmpty())
            throw new IllegalArgumentException("route result has inconsistent readiness/evidence");
        if ((status == Status.ESTIMATED) != estimate.isPresent())
            throw new IllegalArgumentException("regional estimate has inconsistent evidence");
    }
    public PedestrianRouteResult(Status status, List<SurfaceAnchor> route, long workUnits, int regions, String reason) {
        this(status, route, workUnits, regions, reason, java.util.Optional.empty(), java.util.Set.of());
    }
    public boolean awaitable() { return status == Status.PLANNING || status == Status.UNKNOWN_GEOMETRY && !missingGeometry.isEmpty(); }
    public PedestrianRouteResult(Status status, List<SurfaceAnchor> route, long workUnits, int regions, String reason,
                                 java.util.Optional<PedestrianRouteEstimate> estimate) {
        this(status, route, workUnits, regions, reason, estimate, java.util.Set.of());
    }
}
