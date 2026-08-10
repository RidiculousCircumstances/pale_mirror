package io.farfrontier.palemirror.domain;

import java.util.Objects;

/** Pinned deterministic policy values; reload affects only newly registered communities. */
public record SettlementPolicy(WorldObjectId communityId, long rationReserveSteps, long requestReserveSteps,
                               int defenceLossPerUnavailableStep, int stableStepsToRecover) {
    public SettlementPolicy {
        Objects.requireNonNull(communityId, "communityId");
        if (rationReserveSteps < 0 || requestReserveSteps < 0 || requestReserveSteps > rationReserveSteps
                || defenceLossPerUnavailableStep < 0 || stableStepsToRecover < 1) {
            throw new IllegalArgumentException("Invalid settlement policy");
        }
    }
}
