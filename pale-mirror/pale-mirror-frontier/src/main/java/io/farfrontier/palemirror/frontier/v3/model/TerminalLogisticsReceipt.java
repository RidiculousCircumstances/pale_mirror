package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/**
 * Bounded, self-contained evidence retained after a terminal logistics graph is detached from
 * the active world indexes.  It deliberately keeps no route, lease, inventory custody or
 * mutable task reference: those must be closed before compaction is legal.
 */
public record TerminalLogisticsReceipt(SubjectId operationId, SubjectId contractId, SubjectId cargoId,
                               SubjectId settlementId, SubjectId recipientId, List<SubjectId> participants,
                               TerminalLogisticsOutcome outcome, long terminalAtTick) {
    public TerminalLogisticsReceipt {
        Objects.requireNonNull(operationId, "operation id"); Objects.requireNonNull(contractId, "contract id");
        Objects.requireNonNull(cargoId, "cargo id"); Objects.requireNonNull(settlementId, "settlement id");
        Objects.requireNonNull(recipientId, "recipient id"); participants = List.copyOf(participants);
        Objects.requireNonNull(outcome, "terminal outcome");
        if (participants.isEmpty() || participants.size() > 8 || participants.stream().distinct().count() != participants.size()) {
            throw new IllegalArgumentException("terminal receipt must retain one bounded exact participant set");
        }
        if (terminalAtTick < 0L) throw new IllegalArgumentException("terminal receipt tick must be non-negative");
    }

    public enum TerminalLogisticsOutcome {
        DELIVERED, FAILED, INTERRUPTED;

        public int wireTag() { return FrontierWireTags.tag(this); }
    }
}
