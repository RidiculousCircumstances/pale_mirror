package io.farfrontier.palemirror.frontier.v3.api;

/** Stable reason categories for rejected input; callers must not infer state from text. */
public enum RejectionCode {
    WRONG_THREAD,
    WRONG_WORLD,
    STALE_REVISION,
    STALE_SCHEDULE_BINDING,
    DUPLICATE_COMMAND,
    COMMAND_EXPIRED,
    RECEIPT_CAPACITY_EXHAUSTED,
    TRANSACTION_CAPACITY_EXHAUSTED,
    REJECTED_BY_POLICY,
    INVARIANT_FAILURE,
    QUARANTINED
}
