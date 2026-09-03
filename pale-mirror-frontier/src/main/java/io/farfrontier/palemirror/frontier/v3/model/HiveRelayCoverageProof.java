package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Immutable local branch and radius proof retained by a Relay-governed operation. */
public record HiveRelayCoverageProof(SubjectId ganglionId, BlockPosition centre, int radius) {
    public HiveRelayCoverageProof {
        Objects.requireNonNull(ganglionId, "relay ganglion");
        Objects.requireNonNull(centre, "relay coverage centre");
        if (radius <= 0) throw new IllegalArgumentException("relay coverage radius must be positive");
    }

    public boolean covers(BlockPosition position) {
        long dx = (long) position.x() - centre.x();
        long dy = (long) position.y() - centre.y();
        long dz = (long) position.z() - centre.z();
        long squared = Math.addExact(Math.addExact(dx * dx, dy * dy), dz * dz);
        return squared <= (long) radius * radius;
    }
}
