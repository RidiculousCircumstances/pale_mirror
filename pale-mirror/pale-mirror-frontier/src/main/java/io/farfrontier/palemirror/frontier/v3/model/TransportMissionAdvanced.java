package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

public record TransportMissionAdvanced(SubjectId missionId, long expectedRevision, TransportMission.Stage next) implements FrontierPayload {
    public TransportMissionAdvanced {
        Objects.requireNonNull(missionId); Objects.requireNonNull(next);
        if (expectedRevision < 1) throw new IllegalArgumentException("invalid transport transition revision");
    }
    @Override public String type() { return "frontier.transport_mission_advanced"; }
}
