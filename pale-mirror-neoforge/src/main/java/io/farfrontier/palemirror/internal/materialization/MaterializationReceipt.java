package io.farfrontier.palemirror.internal.materialization;

/** Bounded idempotency/diagnostic receipt for a compacted terminal job. */
public record MaterializationReceipt(String jobId, String targetId, String channel,
                                     long desiredRevision, JobState outcome, String diagnostic) {
    public MaterializationReceipt {
        if (jobId == null || jobId.isBlank() || targetId == null || targetId.isBlank()
                || channel == null || channel.isBlank() || desiredRevision < 0 || outcome == null) {
            throw new IllegalArgumentException("Invalid materialization receipt");
        }
        if (outcome != JobState.COMPLETED && outcome != JobState.CANCELLED && outcome != JobState.FAILED) {
            throw new IllegalArgumentException("Receipt outcome must be terminal");
        }
        diagnostic = diagnostic == null ? "" : diagnostic;
    }
}
