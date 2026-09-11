package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.SettlementAssaultSceneCandidate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Immutable pilot-only output of the one read-only server demand seam. */
record FrontierV3PilotSceneDemandSnapshot(Optional<String> providerIdentity, OptionalInt exactCandidateCount,
                                          Optional<BlockPosition> handoffPosition, boolean demandChunkLoaded, Set<UUID> demandObserverIds) {
    FrontierV3PilotSceneDemandSnapshot {
        providerIdentity = Objects.requireNonNull(providerIdentity, "provider identity");
        exactCandidateCount = Objects.requireNonNull(exactCandidateCount, "exact candidate count");
        handoffPosition = Objects.requireNonNull(handoffPosition, "candidate handoff position");
        demandObserverIds = Set.copyOf(Objects.requireNonNull(demandObserverIds, "demand observer ids"));
    }

    static FrontierV3PilotSceneDemandSnapshot unavailable() {
        return new FrontierV3PilotSceneDemandSnapshot(Optional.empty(), OptionalInt.empty(), Optional.empty(), false, Set.of());
    }

    static FrontierV3PilotSceneDemandSnapshot fromProviderCandidates(Optional<String> providerIdentity, List<SettlementAssaultSceneCandidate> candidates,
                                                                      SubjectId assaultId, Function<BlockPosition, FrontierV3SceneDemand.Snapshot> observe) {
        Objects.requireNonNull(providerIdentity, "provider identity"); Objects.requireNonNull(candidates, "provider candidates");
        Objects.requireNonNull(assaultId, "requested assault id"); Objects.requireNonNull(observe, "candidate demand observation");
        if (providerIdentity.isEmpty()) return unavailable();
        List<SettlementAssaultSceneCandidate> exact = candidates.stream().filter(candidate -> candidate.assaultId().equals(assaultId)).toList();
        if (exact.size() != 1) return new FrontierV3PilotSceneDemandSnapshot(providerIdentity, OptionalInt.of(exact.size()), Optional.empty(), false, Set.of());
        BlockPosition handoff = exact.getFirst().handoffPosition();
        FrontierV3SceneDemand.Snapshot demand = observe.apply(handoff);
        return new FrontierV3PilotSceneDemandSnapshot(providerIdentity, OptionalInt.of(1), Optional.of(handoff), demand.chunkLoaded(), demand.observerIds());
    }
}
