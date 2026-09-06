package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Durable COLD hand-off from one exact migration waypoint to its immediate successor. */
public record ResidentMigrationAdvanced(SubjectId residentId, int nextRouteIndex) implements FrontierPayload {
    public ResidentMigrationAdvanced {
        Objects.requireNonNull(residentId, "migration resident");
        if (nextRouteIndex < 1 || nextRouteIndex >= ResidentMigrationJourney.MAX_WAYPOINTS) throw new IllegalArgumentException("migration next route index is out of bounds");
    }
    @Override public String type() { return "frontier.resident_migration_advanced"; }
}
