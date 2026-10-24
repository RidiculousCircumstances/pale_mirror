package io.farfrontier.palemirror.frontier.v3.model;

/** Accepted economic goal. The cultivation owner still selects land and preserves
 * current plants/effects; it never runs an economic solver during execution. */
public record CultivationEconomicIntent(long desiredOutput, long safetyOutput, long horizonTicks, boolean safe) {
    public CultivationEconomicIntent {
        if (desiredOutput < 0 || safetyOutput < 0 || horizonTicks < 1)
            throw new IllegalArgumentException("invalid economic cultivation intent");
    }
}
