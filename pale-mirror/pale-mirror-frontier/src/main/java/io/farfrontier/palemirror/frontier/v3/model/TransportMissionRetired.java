package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** Exact closed aggregate retirement, not permission to discard an unfinished carrier or receipt. */
public record TransportMissionRetired(SubjectId missionId, long expectedRevision) implements FrontierPayload {
    public TransportMissionRetired { Objects.requireNonNull(missionId); if (expectedRevision < 1) throw new IllegalArgumentException("invalid mission revision"); }
    @Override public String type() { return "frontier.transport_mission_retired"; }
}
