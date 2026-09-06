package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Pinned deterministic policy values; reload affects only newly registered communities. */
public record SettlementPolicy(WorldObjectId communityId, long rationReserveSteps, long requestReserveSteps,
                               int defenceLossPerUnavailableStep, int stableStepsToRecover,
                               int evacuationDefenceThreshold, long emergencyGraceSteps,
                               long evacuationDurationSteps) {
    public SettlementPolicy(WorldObjectId communityId, long rationReserveSteps, long requestReserveSteps,
                            int defenceLossPerUnavailableStep, int stableStepsToRecover) {
        this(communityId, rationReserveSteps, requestReserveSteps, defenceLossPerUnavailableStep,
                stableStepsToRecover, 20, 8, 2);
    }
    public SettlementPolicy {
        Objects.requireNonNull(communityId, "communityId");
        if (rationReserveSteps < 0 || requestReserveSteps < 0 || requestReserveSteps > rationReserveSteps
                || defenceLossPerUnavailableStep < 0 || stableStepsToRecover < 1
                || evacuationDefenceThreshold < 0 || evacuationDefenceThreshold > 100
                || emergencyGraceSteps < 1 || evacuationDurationSteps < 1) {
            throw new IllegalArgumentException("Invalid settlement policy");
        }
    }
}
