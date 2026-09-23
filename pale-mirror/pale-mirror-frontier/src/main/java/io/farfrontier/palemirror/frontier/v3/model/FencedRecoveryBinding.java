package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact current authority for one materialized body, cargo, container or effect. */
public record FencedRecoveryBinding(SubjectId bindingId, FencedRecoveryAsset asset, SubjectId ownerId,
                                    long ownerRevision, long authorityEpoch, FencedRecoveryPhase phase,
                                    boolean reversibleCheckpoint, int recoveryAttempts,
                                    FencedRecoveryDisposition nextAction, String reason) {
    public static final int MAX_RECOVERY_ATTEMPTS = 3;

    public FencedRecoveryBinding {
        Objects.requireNonNull(bindingId, "recovery binding id"); Objects.requireNonNull(asset, "recovery asset");
        Objects.requireNonNull(ownerId, "recovery owner"); Objects.requireNonNull(phase, "recovery phase");
        Objects.requireNonNull(nextAction, "recovery next action"); Objects.requireNonNull(reason, "recovery reason");
        if (ownerRevision < 0 || authorityEpoch < 1 || recoveryAttempts < 0 || recoveryAttempts > MAX_RECOVERY_ATTEMPTS
                || reason.isBlank() || reason.length() > 96) throw new IllegalArgumentException("recovery binding version is invalid");
        if (phase != FencedRecoveryPhase.AMBIGUOUS && recoveryAttempts != 0) {
            throw new IllegalArgumentException("only ambiguous recovery may retain retry attempts");
        }
        if (phase == FencedRecoveryPhase.AMBIGUOUS && nextAction != FencedRecoveryDisposition.INSPECT
                && nextAction != FencedRecoveryDisposition.RETRY && nextAction != FencedRecoveryDisposition.REPAIR
                && nextAction != FencedRecoveryDisposition.ABANDON) {
            throw new IllegalArgumentException("ambiguous recovery requires a local inspection, retry, repair, or abandonment action");
        }
    }

    public static FencedRecoveryBinding prepared(SubjectId bindingId, FencedRecoveryAsset asset, SubjectId ownerId,
                                                  long ownerRevision, long authorityEpoch, boolean reversibleCheckpoint) {
        return new FencedRecoveryBinding(bindingId, asset, ownerId, ownerRevision, authorityEpoch,
                FencedRecoveryPhase.PREPARED, reversibleCheckpoint, 0, FencedRecoveryDisposition.RECLAIM, "prepared");
    }

    FencedRecoveryBinding running() {
        require(FencedRecoveryPhase.PREPARED);
        // A scene body's retained COLD pose is the sole reversible attempted projection. Cargo,
        // containers and effects become externally observable attempts as soon as execution
        // starts and must subsequently be inspected rather than rolled back.
        return new FencedRecoveryBinding(bindingId, asset, ownerId, ownerRevision, authorityEpoch,
                FencedRecoveryPhase.RUNNING, asset == FencedRecoveryAsset.BODY && reversibleCheckpoint,
                0, FencedRecoveryDisposition.INSPECT, "physical-attempt");
    }
    FencedRecoveryBinding observed() {
        if (phase != FencedRecoveryPhase.RUNNING && phase != FencedRecoveryPhase.PREPARED) throw new IllegalArgumentException("recovery observation is not current");
        return new FencedRecoveryBinding(bindingId, asset, ownerId, ownerRevision, authorityEpoch,
                FencedRecoveryPhase.OBSERVED, false, 0, FencedRecoveryDisposition.RECLAIM, "physical-observed");
    }
    FencedRecoveryBinding inspectedObserved() {
        // nextAction is a proposed recovery step, not an already committed retirement.
        // Exact evidence may resolve this authority until abandonment actually fences it.
        if (phase != FencedRecoveryPhase.AMBIGUOUS) {
            throw new IllegalArgumentException("recovery inspection is not current");
        }
        return new FencedRecoveryBinding(bindingId, asset, ownerId, ownerRevision, authorityEpoch,
                FencedRecoveryPhase.OBSERVED, false, 0, FencedRecoveryDisposition.RECLAIM, "physical-observed-after-inspection");
    }
    FencedRecoveryBinding confirmed() {
        if (phase != FencedRecoveryPhase.OBSERVED) throw new IllegalArgumentException("only observed recovery evidence can confirm a consequence");
        return new FencedRecoveryBinding(bindingId, asset, ownerId, ownerRevision, authorityEpoch,
                FencedRecoveryPhase.CONFIRMED, false, 0, FencedRecoveryDisposition.REJECT_STALE, "confirmed");
    }
    FencedRecoveryBinding ambiguous(String nextReason, FencedRecoveryDisposition action) {
        if (phase == FencedRecoveryPhase.CONFIRMED) throw new IllegalArgumentException("confirmed consequence cannot become ambiguous");
        int attempts = phase == FencedRecoveryPhase.AMBIGUOUS ? recoveryAttempts + 1 : 1;
        if (attempts >= MAX_RECOVERY_ATTEMPTS) action = FencedRecoveryDisposition.ABANDON;
        return new FencedRecoveryBinding(bindingId, asset, ownerId, ownerRevision, authorityEpoch,
                FencedRecoveryPhase.AMBIGUOUS, false, Math.min(attempts, MAX_RECOVERY_ATTEMPTS), action, nextReason);
    }
    void require(FencedRecoveryPhase expected) {
        if (phase != expected) throw new IllegalArgumentException("recovery phase is stale or incompatible");
    }
}
