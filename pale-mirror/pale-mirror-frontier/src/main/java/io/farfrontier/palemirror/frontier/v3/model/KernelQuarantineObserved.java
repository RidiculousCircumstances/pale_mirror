package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Kernel-originated invariant boundary with a declared producer, never an exception classifier. */
public record KernelQuarantineObserved(SubjectId frontierId, Producer producer, String failureIdentity) implements FrontierPayload {
    public enum Producer {
        COMMAND_TRANSACTION(DiagnosticReason.FRONTIER_KERNEL_COMMAND_FAILURE),
        DUE_CAPACITY(DiagnosticReason.FRONTIER_KERNEL_TRANSACTION_CAPACITY),
        DUE_TRANSACTION(DiagnosticReason.FRONTIER_KERNEL_DUE_FAILURE);
        private final DiagnosticReason reason;
        Producer(DiagnosticReason reason) { this.reason = reason; }
        public DiagnosticReason diagnosticReason() { return reason; }
        public DiagnosticTuple stamp(SubjectId frontierId) { return new DiagnosticTuple(reason, reason.category(),
                new DiagnosticOwner(DiagnosticOwnerKind.FRONTIER_INSTANCE, frontierId),
                new DiagnosticSubject(DiagnosticSubjectKind.FRONTIER_INSTANCE, frontierId), reason.disposition()); }
    }
    public KernelQuarantineObserved {
        frontierId = Objects.requireNonNull(frontierId, "kernel frontier id"); producer = Objects.requireNonNull(producer, "kernel producer");
        failureIdentity = Objects.requireNonNull(failureIdentity, "kernel failure identity");
        if (frontierId.value().isBlank() || failureIdentity.isBlank() || failureIdentity.length() > 160) throw new IllegalArgumentException("invalid kernel quarantine evidence");
    }
    public DiagnosticTuple diagnostic() { return producer.stamp(frontierId); }
    @Override public String type() { return "frontier.kernel_quarantine_observed"; }
}
