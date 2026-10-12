package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.model.expedition.ExpeditionJourneyProof;

public record TransportMissionAdvanced(SubjectId missionId, long expectedRevision, TransportMission.Stage next,
                                       Optional<ExpeditionJourneyProof> departure) implements FrontierPayload {
    public TransportMissionAdvanced(SubjectId id, long revision, TransportMission.Stage next) {
        this(id, revision, next, Optional.empty());
    }
    public TransportMissionAdvanced {
        Objects.requireNonNull(missionId); Objects.requireNonNull(next);
        Objects.requireNonNull(departure);
        if (next != TransportMission.Stage.OUTBOUND && departure.isPresent())
            throw new IllegalArgumentException("only outbound admission may carry a departure proof");
        if (expectedRevision < 1) throw new IllegalArgumentException("invalid transport transition revision");
    }
    @Override public String type() { return "frontier.transport_mission_advanced"; }
}
