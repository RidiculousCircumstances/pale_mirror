package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Kernel-originated invariant boundary with a declared producer, never an exception classifier. */
public record KernelQuarantineObserved(SubjectId frontierId, Producer producer, String failureIdentity) implements FrontierPayload {
    public enum Producer {
        COMMAND_TRANSACTION(0, DiagnosticReason.FRONTIER_KERNEL_COMMAND_FAILURE),
        DUE_CAPACITY(1, DiagnosticReason.FRONTIER_KERNEL_TRANSACTION_CAPACITY),
        DUE_TRANSACTION(2, DiagnosticReason.FRONTIER_KERNEL_DUE_FAILURE);
        private final int wireTag;
        private final DiagnosticReason reason;
        Producer(int wireTag, DiagnosticReason reason) { this.wireTag = wireTag; this.reason = reason; }
        public int wireTag() { return wireTag; }
        public static Producer fromWireTag(int tag) {
            for (Producer producer : values()) if (producer.wireTag == tag) return producer;
            throw new IllegalArgumentException("invalid kernel quarantine producer");
        }
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
