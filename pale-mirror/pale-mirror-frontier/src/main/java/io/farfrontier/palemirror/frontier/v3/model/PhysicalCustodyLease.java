package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Epoch-fenced authority for one exact physical scope; never an inventory or scene lease. */
public record PhysicalCustodyLease(SubjectId scopeId, SubjectId objectId, SubjectId providerId, long authorityEpoch,
                                   long expectedCanonicalRevision, long expectedReplicaRevision,
                                   PhysicalCustodyLeaseStatus status, PhysicalCustodyUnresolvedReason unresolvedReason) {
    public PhysicalCustodyLease {
        Objects.requireNonNull(scopeId, "scope id"); Objects.requireNonNull(objectId, "object id"); Objects.requireNonNull(providerId, "provider id");
        Objects.requireNonNull(status, "custody status");
        if (authorityEpoch < 1 || expectedCanonicalRevision < 0 || expectedReplicaRevision < 0
                || (status == PhysicalCustodyLeaseStatus.UNRESOLVED) != (unresolvedReason != null)) {
            throw new IllegalArgumentException("custody lease version is invalid");
        }
    }
    public boolean live() { return status != PhysicalCustodyLeaseStatus.RELEASED; }
    public PhysicalCustodyLease checkpoint(long canonicalRevision, long replicaRevision) {
        if (status != PhysicalCustodyLeaseStatus.ACQUIRED || canonicalRevision != expectedCanonicalRevision || replicaRevision != expectedReplicaRevision) throw new IllegalArgumentException("custody checkpoint fence is stale or forged");
        return new PhysicalCustodyLease(scopeId, objectId, providerId, authorityEpoch, expectedCanonicalRevision, expectedReplicaRevision,
                PhysicalCustodyLeaseStatus.CHECKPOINTED, null);
    }
    public PhysicalCustodyLease unresolved(PhysicalCustodyUnresolvedReason reason) {
        if (status != PhysicalCustodyLeaseStatus.ACQUIRED && status != PhysicalCustodyLeaseStatus.CHECKPOINTED) {
            throw new IllegalArgumentException("custody unresolved transition is illegal");
        }
        return new PhysicalCustodyLease(scopeId, objectId, providerId, authorityEpoch, expectedCanonicalRevision, expectedReplicaRevision,
                PhysicalCustodyLeaseStatus.UNRESOLVED, Objects.requireNonNull(reason, "unresolved reason"));
    }
    public PhysicalCustodyLease release(long canonicalRevision, long replicaRevision) {
        if (!live() || status == PhysicalCustodyLeaseStatus.UNRESOLVED || canonicalRevision != expectedCanonicalRevision
                || replicaRevision != expectedReplicaRevision) throw new IllegalArgumentException("custody release fence is stale, forged, or unresolved");
        return new PhysicalCustodyLease(scopeId, objectId, providerId, authorityEpoch, expectedCanonicalRevision, expectedReplicaRevision,
                PhysicalCustodyLeaseStatus.RELEASED, null);
    }
}
