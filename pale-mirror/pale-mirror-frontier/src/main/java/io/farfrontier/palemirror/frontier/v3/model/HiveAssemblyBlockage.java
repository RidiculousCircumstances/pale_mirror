package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/**
 * Exact physical fact that stopped one retained hive-assembly edge.
 *
 * <p>This is evidence, not a permission to reroute.  The canonical reducer validates it
 * against the persisted member cursor before the mobilization enters {@code CONFLICT}.</p>
 */
public record HiveAssemblyBlockage(SubjectId actorId, int expectedCursor, SurfaceAnchor target) {
    public HiveAssemblyBlockage {
        actorId = Objects.requireNonNull(actorId, "blocked hive assembly actor");
        if (expectedCursor < 0 || expectedCursor > TraversalTopology.MAX_NODES) {
            throw new IllegalArgumentException("blocked hive assembly cursor is outside topology bounds");
        }
        target = Objects.requireNonNull(target, "blocked hive assembly target");
    }
}
