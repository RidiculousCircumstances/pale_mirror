package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Observed completion of one exact HOT transit segment by its already leased resident body. */
public record ResidentTransitAdvanced(SubjectId residentId, int nextRouteIndex) implements FrontierPayload {
    public ResidentTransitAdvanced {
        Objects.requireNonNull(residentId, "migration resident");
        if (nextRouteIndex < 1 || nextRouteIndex >= ResidentMigrationJourney.MAX_WAYPOINTS) {
            throw new IllegalArgumentException("HOT transit next route index is out of bounds");
        }
    }
    @Override public String type() { return "frontier.resident_transit_advanced"; }
}
