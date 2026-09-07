package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Versioned, non-authoritative evidence for one exact emitted physical object. */
public record PhysicalReplicaRecord(SubjectId objectId, String semanticKind, long emittedCanonicalRevision,
                                    long observedCanonicalRevision, String fingerprint, String provenance,
                                    PhysicalReplicaState state) {
    public PhysicalReplicaRecord {
        Objects.requireNonNull(objectId, "object id"); Objects.requireNonNull(semanticKind, "semantic kind");
        Objects.requireNonNull(fingerprint, "replica fingerprint"); Objects.requireNonNull(provenance, "replica provenance");
        Objects.requireNonNull(state, "replica state");
        if (semanticKind.isBlank() || fingerprint.isBlank() || provenance.isBlank() || emittedCanonicalRevision < 0
                || observedCanonicalRevision < emittedCanonicalRevision) throw new IllegalArgumentException("replica record is invalid");
    }
    public static PhysicalReplicaRecord expected(SubjectId objectId, String semanticKind, long canonicalRevision,
                                                 String fingerprint, String provenance) {
        return new PhysicalReplicaRecord(objectId, semanticKind, canonicalRevision, canonicalRevision, fingerprint, provenance,
                PhysicalReplicaState.EXPECTED);
    }
    public PhysicalReplicaRecord observe(String observedFingerprint, String observedProvenance, long revision) {
        Objects.requireNonNull(observedFingerprint, "observed fingerprint");
        Objects.requireNonNull(observedProvenance, "observed provenance");
        if (state == PhysicalReplicaState.CONFLICT || revision < observedCanonicalRevision) throw new IllegalArgumentException("replica observation is stale");
        if (!observedFingerprint.equals(fingerprint) || !observedProvenance.equals(provenance)) {
            return new PhysicalReplicaRecord(objectId, semanticKind, emittedCanonicalRevision, revision, fingerprint, provenance,
                    PhysicalReplicaState.CONFLICT);
        }
        return new PhysicalReplicaRecord(objectId, semanticKind, emittedCanonicalRevision, revision, fingerprint, provenance,
                PhysicalReplicaState.OBSERVED_CURRENT);
    }
}
