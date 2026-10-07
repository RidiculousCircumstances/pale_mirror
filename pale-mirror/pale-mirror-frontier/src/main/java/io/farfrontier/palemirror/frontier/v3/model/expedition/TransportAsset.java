package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Objects;
import java.util.Optional;

/** Finite transport capability. Motion, life and stock belong to their common owners. */
public record TransportAsset(SubjectId actorId, SubjectId homeSettlementId, Kind kind,
                             SubjectId containerId, int stackSlots, SurfaceAnchor homeStation,
                             Optional<SubjectId> missionId) {
    public enum Kind { CHEST_DONKEY }
    public TransportAsset {
        Objects.requireNonNull(actorId); Objects.requireNonNull(homeSettlementId); Objects.requireNonNull(kind);
        Objects.requireNonNull(containerId); Objects.requireNonNull(homeStation); Objects.requireNonNull(missionId);
        if (stackSlots < 1 || stackSlots > 15) throw new IllegalArgumentException("invalid pack transport capacity");
    }
    public TransportAsset reserve(SubjectId mission) {
        if (missionId.isPresent()) throw new IllegalArgumentException("transport asset already reserved");
        return new TransportAsset(actorId, homeSettlementId, kind, containerId, stackSlots, homeStation, Optional.of(mission));
    }
    public TransportAsset release(SubjectId mission) {
        if (!missionId.equals(Optional.of(mission))) throw new IllegalArgumentException("foreign transport asset release");
        return new TransportAsset(actorId, homeSettlementId, kind, containerId, stackSlots, homeStation, Optional.empty());
    }
}
