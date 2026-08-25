package io.farfrontier.palemirror.frontier.reference;

import java.util.Objects;

/**
 * Bounded audit receipt for one source V2 settlement decision.
 *
 * <p>Receipts are deliberately diagnostic only: the active civic, perception,
 * charter and campaign owners remain the only inputs to future planning.</p>
 */
public record ReferenceV2DecisionReceipt(
        int day,
        int settlementId,
        String action,
        double risk,
        String reason,
        String blockers
) {
    public ReferenceV2DecisionReceipt {
        action = Objects.requireNonNull(action, "action");
        reason = Objects.requireNonNull(reason, "reason");
        blockers = Objects.requireNonNull(blockers, "blockers");
    }
}
