package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** The current exact quarantine policy for one settlement. */
public record SettlementQuarantine(SettlementQuarantineStatus status, long sinceTick) {
    public SettlementQuarantine {
        Objects.requireNonNull(status, "settlement quarantine status");
    }

    static SettlementQuarantine normalAt(long tick) { return new SettlementQuarantine(SettlementQuarantineStatus.NORMAL, tick); }

    SettlementQuarantine transition(SettlementQuarantineStatus next, long tick) {
        Objects.requireNonNull(next, "next settlement quarantine status");
        if (tick < sinceTick) throw new IllegalArgumentException("settlement quarantine cannot move backwards in time");
        if (status == next) throw new IllegalArgumentException("settlement quarantine is already " + next.name().toLowerCase(java.util.Locale.ROOT));
        return new SettlementQuarantine(next, tick);
    }
}
