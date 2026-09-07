package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Optional;

/** Immutable diagnostic-only view; it exposes no mutation or physical-world truth. */
public record PhysicalReplicaCustodyProjection(List<Entry> entries) {
    public PhysicalReplicaCustodyProjection { entries = List.copyOf(entries); }
    public record Entry(SubjectId objectId, String semanticKind, long emittedCanonicalRevision, long observedCanonicalRevision, long replicaRevision,
                        String fingerprint, String provenance, PhysicalReplicaState replicaState, Optional<SubjectId> scopeId,
                        Optional<SubjectId> providerId, Optional<Long> authorityEpoch, Optional<PhysicalCustodyLeaseStatus> custodyStatus,
                        Optional<PhysicalCustodyUnresolvedReason> unresolvedReason) {
        public Entry {
            scopeId = Optional.ofNullable(scopeId).orElseThrow(() -> new NullPointerException("scope id"));
            providerId = Optional.ofNullable(providerId).orElseThrow(() -> new NullPointerException("provider id"));
            authorityEpoch = Optional.ofNullable(authorityEpoch).orElseThrow(() -> new NullPointerException("authority epoch"));
            custodyStatus = Optional.ofNullable(custodyStatus).orElseThrow(() -> new NullPointerException("custody status"));
            unresolvedReason = Optional.ofNullable(unresolvedReason).orElseThrow(() -> new NullPointerException("unresolved reason"));
            if (scopeId.isPresent() != providerId.isPresent() || scopeId.isPresent() != authorityEpoch.isPresent() || scopeId.isPresent() != custodyStatus.isPresent()
                    || unresolvedReason.isPresent() && custodyStatus.orElseThrow() != PhysicalCustodyLeaseStatus.UNRESOLVED) throw new IllegalArgumentException("custody diagnostic is incomplete");
        }
    }
}
