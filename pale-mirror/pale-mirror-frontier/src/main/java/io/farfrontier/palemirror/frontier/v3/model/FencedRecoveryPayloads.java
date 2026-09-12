package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Closed WAL vocabulary for recovery authority; each mutation is fenced by exact identity/epoch. */
public final class FencedRecoveryPayloads {
    private FencedRecoveryPayloads() { }
    public record Prepared(FencedRecoveryBinding binding) implements FrontierPayload {
        public Prepared { Objects.requireNonNull(binding, "recovery binding"); }
        @Override public String type() { return "frontier.fenced_recovery_prepared"; }
        @Override public boolean requiresDurableBeforeEffect() { return true; }
    }
    public record Running(SubjectId bindingId, long expectedEpoch) implements FrontierPayload {
        public Running { require(bindingId, expectedEpoch); }
        @Override public String type() { return "frontier.fenced_recovery_running"; }
        @Override public boolean requiresDurableBeforeEffect() { return true; }
    }
    public record Observed(SubjectId bindingId, long expectedEpoch) implements FrontierPayload {
        public Observed { require(bindingId, expectedEpoch); }
        @Override public String type() { return "frontier.fenced_recovery_observed"; }
    }
    public record Confirmed(SubjectId bindingId, long expectedEpoch) implements FrontierPayload {
        public Confirmed { require(bindingId, expectedEpoch); }
        @Override public String type() { return "frontier.fenced_recovery_confirmed"; }
    }
    public record RevokedToCold(SubjectId bindingId, long expectedEpoch) implements FrontierPayload {
        public RevokedToCold { require(bindingId, expectedEpoch); }
        @Override public String type() { return "frontier.fenced_recovery_revoked_to_cold"; }
    }
    public record Ambiguous(SubjectId bindingId, long expectedEpoch, String reason, FencedRecoveryDisposition action) implements FrontierPayload {
        public Ambiguous { require(bindingId, expectedEpoch); Objects.requireNonNull(reason, "recovery reason"); Objects.requireNonNull(action, "recovery action"); if (reason.isBlank() || reason.length() > 96) throw new IllegalArgumentException("recovery ambiguity reason is invalid"); }
        @Override public String type() { return "frontier.fenced_recovery_ambiguous"; }
    }
    public record Abandoned(SubjectId bindingId, long expectedEpoch) implements FrontierPayload {
        public Abandoned { require(bindingId, expectedEpoch); }
        @Override public String type() { return "frontier.fenced_recovery_abandoned"; }
    }
    private static void require(SubjectId id, long epoch) { Objects.requireNonNull(id, "recovery binding id"); if (epoch < 1) throw new IllegalArgumentException("recovery epoch is invalid"); }
}
