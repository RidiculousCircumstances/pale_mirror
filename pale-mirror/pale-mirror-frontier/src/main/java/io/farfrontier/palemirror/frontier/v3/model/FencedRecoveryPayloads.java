package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Closed WAL vocabulary for recovery authority; each mutation is fenced by exact identity/epoch. */
public final class FencedRecoveryPayloads {
    private FencedRecoveryPayloads() { }
    /** Generic assets only; actor bodies enter their own exact lifecycle boundary. */
    public sealed interface ExistingBindingTransition extends FrontierPayload
            permits Running, Observed, Confirmed, RevokedToCold, Ambiguous, Abandoned {
        SubjectId bindingId();
        long expectedEpoch();
    }
    /** The physical provider supplies this only after exact successful write and durable sync. */
    public record CargoCleanupSaved(CargoProjectionRetirement retirement) implements FrontierPayload {
        public CargoCleanupSaved { Objects.requireNonNull(retirement, "saved cargo retirement"); }
        @Override public String type() { return "frontier.cargo_cleanup_saved"; }
        @Override public boolean requiresDurableBeforeEffect() { return true; }
    }
    public record Prepared(FencedRecoveryBinding binding) implements FrontierPayload {
        public Prepared {
            Objects.requireNonNull(binding, "recovery binding");
            if (binding.asset() == FencedRecoveryAsset.BODY)
                throw new IllegalArgumentException("body admission belongs exclusively to actor body lifecycle");
        }
        @Override public String type() { return "frontier.fenced_recovery_prepared"; }
        @Override public boolean requiresDurableBeforeEffect() { return true; }
    }
    public record Running(SubjectId bindingId, long expectedEpoch) implements ExistingBindingTransition {
        public Running { require(bindingId, expectedEpoch); }
        @Override public String type() { return "frontier.fenced_recovery_running"; }
        @Override public boolean requiresDurableBeforeEffect() { return true; }
    }
    public record Observed(SubjectId bindingId, long expectedEpoch) implements ExistingBindingTransition {
        public Observed { require(bindingId, expectedEpoch); }
        @Override public String type() { return "frontier.fenced_recovery_observed"; }
    }
    public record Confirmed(SubjectId bindingId, long expectedEpoch) implements ExistingBindingTransition {
        public Confirmed { require(bindingId, expectedEpoch); }
        @Override public String type() { return "frontier.fenced_recovery_confirmed"; }
    }
    public record RevokedToCold(SubjectId bindingId, long expectedEpoch) implements ExistingBindingTransition {
        public RevokedToCold { require(bindingId, expectedEpoch); }
        @Override public String type() { return "frontier.fenced_recovery_revoked_to_cold"; }
    }
    /**
     * A potentially applied physical consequence is locally unknown.  The replica-custody
     * producer stamps this exact binding before the ambiguity can enter canonical recovery.
     */
    public record Ambiguous(SubjectId bindingId, long expectedEpoch, String reason, FencedRecoveryDisposition action,
                            DiagnosticTuple diagnostic) implements ExistingBindingTransition {
        public Ambiguous {
            require(bindingId, expectedEpoch); Objects.requireNonNull(reason, "recovery reason");
            Objects.requireNonNull(action, "recovery action");
            diagnostic = Objects.requireNonNull(diagnostic, "recovery ambiguity diagnostic");
            if (reason.isBlank() || reason.length() > 96) throw new IllegalArgumentException("recovery ambiguity reason is invalid");
            FencedRecoveryDiagnosticProducer.requireExact(bindingId, diagnostic);
        }
        @Override public String type() { return "frontier.fenced_recovery_ambiguous"; }
    }
    public record Abandoned(SubjectId bindingId, long expectedEpoch) implements ExistingBindingTransition {
        public Abandoned { require(bindingId, expectedEpoch); }
        @Override public String type() { return "frontier.fenced_recovery_abandoned"; }
    }
    private static void require(SubjectId id, long epoch) { Objects.requireNonNull(id, "recovery binding id"); if (epoch < 1) throw new IllegalArgumentException("recovery epoch is invalid"); }
}
