package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Persisted authority for one exact hive operation.  It is deliberately not a body,
 * vitality or lease record: ActorLocation and AmbientActorLease remain those owners.
 */
public record HiveOperationCommandAuthority(HiveCommandAuthorityKind kind, SubjectId originalAuthorityId,
                                            SubjectId currentAuthorityId, List<SubjectId> rosterIds,
                                            int subordinateWeight, Optional<HiveRelayCoverageProof> relayCoverage,
                                            HiveCommandSignalPhase signalPhase, long signalSince) {
    public HiveOperationCommandAuthority {
        Objects.requireNonNull(kind, "hive command kind");
        Objects.requireNonNull(originalAuthorityId, "original hive command authority");
        Objects.requireNonNull(currentAuthorityId, "current hive command authority");
        rosterIds = List.copyOf(Objects.requireNonNull(rosterIds, "hive command roster"));
        relayCoverage = Objects.requireNonNull(relayCoverage, "relay coverage proof");
        Objects.requireNonNull(signalPhase, "hive signal phase");
        if (rosterIds.isEmpty() || rosterIds.stream().distinct().count() != rosterIds.size()) {
            throw new IllegalArgumentException("hive command roster must be non-empty and exact");
        }
        if (subordinateWeight < 0 || signalSince < 0L) throw new IllegalArgumentException("invalid hive command balance or instant");
        if (kind == HiveCommandAuthorityKind.RELAY) {
            if (relayCoverage.isEmpty() || !originalAuthorityId.equals(currentAuthorityId) || subordinateWeight != 0) {
                throw new IllegalArgumentException("Relay authority requires one unchanged local coverage proof");
            }
        } else if (relayCoverage.isPresent() || !rosterIds.contains(originalAuthorityId)
                || signalPhase != HiveCommandSignalPhase.RECLAIMED && !rosterIds.contains(currentAuthorityId)) {
            throw new IllegalArgumentException("Overseer authority requires its exact roster and no Relay proof");
        }
        if (signalPhase == HiveCommandSignalPhase.RECLAIMED && kind != HiveCommandAuthorityKind.OVERSEER) {
            throw new IllegalArgumentException("only an Overseer authority can reclaim a hive operation");
        }
    }

    public boolean permitsCoordinatedAdvance() {
        return signalPhase == HiveCommandSignalPhase.CONNECTED || signalPhase == HiveCommandSignalPhase.SIGNAL_MEMORY
                || signalPhase == HiveCommandSignalPhase.RECLAIMED;
    }

    public HiveOperationCommandAuthority withSignal(HiveCommandSignalPhase next, long at) {
        if (next == HiveCommandSignalPhase.RECLAIMED) throw new IllegalArgumentException("reclaim requires an exact controller");
        return new HiveOperationCommandAuthority(kind, originalAuthorityId, currentAuthorityId, rosterIds, subordinateWeight,
                relayCoverage, next, at);
    }

    public HiveOperationCommandAuthority reclaim(SubjectId overseerId, long at) {
        if (kind != HiveCommandAuthorityKind.OVERSEER || signalPhase != HiveCommandSignalPhase.INSTINCT) {
            throw new IllegalArgumentException("only instinct-bound mobile authority can be reclaimed");
        }
        return new HiveOperationCommandAuthority(kind, originalAuthorityId, Objects.requireNonNull(overseerId, "reclaiming overseer"),
                rosterIds, subordinateWeight, Optional.empty(), HiveCommandSignalPhase.RECLAIMED, at);
    }
}
