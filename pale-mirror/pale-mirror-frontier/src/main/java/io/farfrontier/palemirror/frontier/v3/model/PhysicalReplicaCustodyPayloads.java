package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Closed command/event payload vocabulary for the pure replica/custody registry. */
public final class PhysicalReplicaCustodyPayloads {
    private PhysicalReplicaCustodyPayloads() { }

    public record ReplicaDeclared(PhysicalReplicaRecord replica) implements FrontierPayload {
        public ReplicaDeclared { Objects.requireNonNull(replica, "replica"); }
        @Override public String type() { return "frontier.physical_replica_declared"; }
    }
    public record ReplicaObserved(SubjectId objectId, long expectedReplicaRevision, String fingerprint, String provenance,
                                  long observedCanonicalRevision) implements FrontierPayload {
        public ReplicaObserved {
            Objects.requireNonNull(objectId, "object id"); Objects.requireNonNull(fingerprint, "fingerprint"); Objects.requireNonNull(provenance, "provenance");
            if (expectedReplicaRevision < 0 || observedCanonicalRevision < 0) throw new IllegalArgumentException("replica observation revision is invalid");
        }
        @Override public String type() { return "frontier.physical_replica_observed"; }
    }
    public record CustodyAcquired(PhysicalCustodyLease lease) implements FrontierPayload {
        public CustodyAcquired { Objects.requireNonNull(lease, "lease"); }
        @Override public String type() { return "frontier.physical_custody_acquired"; }
        @Override public boolean requiresDurableBeforeEffect() { return true; }
    }
    public record CustodyCheckpointed(SubjectId scopeId, long expectedEpoch, long expectedCanonicalRevision,
                                      long expectedReplicaRevision) implements FrontierPayload {
        public CustodyCheckpointed { Objects.requireNonNull(scopeId, "scope id"); if (expectedEpoch < 1 || expectedCanonicalRevision < 0 || expectedReplicaRevision < 0) throw new IllegalArgumentException("custody checkpoint is invalid"); }
        @Override public String type() { return "frontier.physical_custody_checkpointed"; }
        @Override public boolean requiresDurableBeforeEffect() { return true; }
    }
    public record CustodyUnresolved(SubjectId scopeId, long expectedEpoch, PhysicalCustodyUnresolvedReason reason) implements FrontierPayload {
        public CustodyUnresolved { Objects.requireNonNull(scopeId, "scope id"); Objects.requireNonNull(reason, "reason"); if (expectedEpoch < 1) throw new IllegalArgumentException("custody epoch is invalid"); }
        @Override public String type() { return "frontier.physical_custody_unresolved"; }
    }
    public record CustodyReleased(SubjectId scopeId, long expectedEpoch, long expectedCanonicalRevision,
                                  long expectedReplicaRevision) implements FrontierPayload {
        public CustodyReleased { Objects.requireNonNull(scopeId, "scope id"); if (expectedEpoch < 1 || expectedCanonicalRevision < 0 || expectedReplicaRevision < 0) throw new IllegalArgumentException("custody release is invalid"); }
        @Override public String type() { return "frontier.physical_custody_released"; }
        @Override public boolean requiresDurableBeforeEffect() { return true; }
    }
}
