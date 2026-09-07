package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Versioned, non-authoritative evidence for one exact emitted physical object. */
public record PhysicalReplicaRecord(SubjectId objectId, String semanticKind, long emittedCanonicalRevision,
                                    long observedCanonicalRevision, long replicaRevision, String fingerprint, String provenance,
                                    PhysicalReplicaState state) {
    public PhysicalReplicaRecord {
        Objects.requireNonNull(objectId, "object id"); Objects.requireNonNull(semanticKind, "semantic kind");
        Objects.requireNonNull(fingerprint, "replica fingerprint"); Objects.requireNonNull(provenance, "replica provenance");
        Objects.requireNonNull(state, "replica state");
        if (semanticKind.isBlank() || fingerprint.isBlank() || provenance.isBlank() || emittedCanonicalRevision < 0
                || observedCanonicalRevision != emittedCanonicalRevision || replicaRevision < 1) {
            throw new IllegalArgumentException("replica record is invalid");
        }
    }
    public static PhysicalReplicaRecord expected(SubjectId objectId, String semanticKind, long canonicalRevision,
                                                 String fingerprint, String provenance) {
        return new PhysicalReplicaRecord(objectId, semanticKind, canonicalRevision, canonicalRevision, 1L, fingerprint, provenance,
                PhysicalReplicaState.EXPECTED);
    }
    /**
     * This kernel has no canonical producer: an observation can only confirm or conflict with the exact emitted
     * canonical evidence.  A later canonical projection must declare a new replica instead of inventing a revision.
     */
    public PhysicalReplicaRecord observe(long expectedCanonicalRevision, String observedFingerprint, String observedProvenance,
                                         long observedRevision) {
        Objects.requireNonNull(observedFingerprint, "observed fingerprint");
        Objects.requireNonNull(observedProvenance, "observed provenance");
        if (state == PhysicalReplicaState.CONFLICT || expectedCanonicalRevision != emittedCanonicalRevision
                || observedRevision != emittedCanonicalRevision) throw new IllegalArgumentException("replica observation is stale or forged");
        if (!observedFingerprint.equals(fingerprint) || !observedProvenance.equals(provenance)) {
            return new PhysicalReplicaRecord(objectId, semanticKind, emittedCanonicalRevision, observedRevision, replicaRevision + 1, fingerprint, provenance,
                    PhysicalReplicaState.CONFLICT);
        }
        return new PhysicalReplicaRecord(objectId, semanticKind, emittedCanonicalRevision, observedRevision, replicaRevision + 1, fingerprint, provenance,
                PhysicalReplicaState.OBSERVED_CURRENT);
    }
}
