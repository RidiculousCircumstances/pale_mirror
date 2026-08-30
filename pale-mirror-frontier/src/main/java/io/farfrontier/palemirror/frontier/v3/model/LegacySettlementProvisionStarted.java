package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * Snapshot/WAL migration boundary for the pre-recipient provision event.  It is decoded only
 * from retained v57 WAL; the reducer deterministically upgrades it against the current exact
 * resident register before it can mutate state.
 */
record LegacySettlementProvisionStarted(SubjectId settlementId, int cycleOrdinal, long startedAtTick, int requiredRations,
                                        int fulfilledRations, List<LegacyAllocation> allocations, int nextAllocation,
                                        SettlementProvisionStatus status) implements FrontierPayload {
    record LegacyAllocation(SubjectId itemId, int count) {
        LegacyAllocation {
            Objects.requireNonNull(itemId, "legacy provision item");
            if (count < 1 || count > 64) throw new IllegalArgumentException("legacy provision count must be 1..64");
        }
    }

    LegacySettlementProvisionStarted {
        Objects.requireNonNull(settlementId, "legacy provision settlement"); Objects.requireNonNull(allocations, "legacy provision allocations");
        if (cycleOrdinal < 0 || startedAtTick < 0 || requiredRations < 0 || fulfilledRations < 0 || fulfilledRations > requiredRations
                || nextAllocation < 0 || nextAllocation > allocations.size()) throw new IllegalArgumentException("invalid legacy provision counters");
        allocations = List.copyOf(allocations); Objects.requireNonNull(status, "legacy provision status");
    }

    @Override public String type() { return "frontier.settlement_provision_started"; }
}
