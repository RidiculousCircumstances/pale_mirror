package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;

/** Versioned, non-authoritative evidence for one exact emitted physical object. */
public record PhysicalReplicaRecord(SubjectId objectId, String semanticKind, long emittedCanonicalRevision,
                                    long observedCanonicalRevision, long replicaRevision, String fingerprint, String provenance,
                                    PhysicalReplicaState state, Optional<String> observedFingerprint,
                                    Optional<String> observedProvenance, Optional<PhysicalReplicaConflictReason> conflictReason) {
    public PhysicalReplicaRecord {
        Objects.requireNonNull(objectId, "object id"); Objects.requireNonNull(semanticKind, "semantic kind");
        Objects.requireNonNull(fingerprint, "replica fingerprint"); Objects.requireNonNull(provenance, "replica provenance");
        Objects.requireNonNull(state, "replica state");
        observedFingerprint = Objects.requireNonNull(observedFingerprint, "observed fingerprint");
        observedProvenance = Objects.requireNonNull(observedProvenance, "observed provenance");
        conflictReason = Objects.requireNonNull(conflictReason, "conflict reason");
        if (semanticKind.isBlank() || fingerprint.isBlank() || provenance.isBlank() || emittedCanonicalRevision < 0
                || observedCanonicalRevision != emittedCanonicalRevision || replicaRevision < 1
                || (state == PhysicalReplicaState.CONFLICT) != conflictReason.isPresent()
                || conflictReason.isPresent() != observedFingerprint.isPresent()
                || observedFingerprint.isPresent() != observedProvenance.isPresent()) {
            throw new IllegalArgumentException("replica record is invalid");
        }
    }
    public static PhysicalReplicaRecord expected(SubjectId objectId, String semanticKind, long canonicalRevision,
                                                 String fingerprint, String provenance) {
        return new PhysicalReplicaRecord(objectId, semanticKind, canonicalRevision, canonicalRevision, 1L, fingerprint, provenance,
                PhysicalReplicaState.EXPECTED, Optional.empty(), Optional.empty(), Optional.empty());
    }
    public PhysicalReplicaRecord reemit(long canonicalRevision, String nextFingerprint, String nextProvenance) {
        Objects.requireNonNull(nextFingerprint, "next fingerprint"); Objects.requireNonNull(nextProvenance, "next provenance");
        if (state != PhysicalReplicaState.OBSERVED_CURRENT || canonicalRevision <= emittedCanonicalRevision
                || nextFingerprint.isBlank() || nextProvenance.isBlank()) throw new IllegalArgumentException("replica emission is stale or invalid");
        return new PhysicalReplicaRecord(objectId, semanticKind, canonicalRevision, canonicalRevision, replicaRevision + 1,
                nextFingerprint, nextProvenance, PhysicalReplicaState.EXPECTED, Optional.empty(), Optional.empty(), Optional.empty());
    }
    /**
     * This kernel has no canonical producer: an observation can only confirm or conflict with the exact emitted
     * canonical evidence.  A later canonical projection must declare a new replica instead of inventing a revision.
     */
    public PhysicalReplicaRecord observe(long expectedCanonicalRevision, String observedFingerprint, String observedProvenance,
                                         long observedRevision) {
        Objects.requireNonNull(observedFingerprint, "observed fingerprint");
        Objects.requireNonNull(observedProvenance, "observed provenance");
        if (state != PhysicalReplicaState.EXPECTED || expectedCanonicalRevision != emittedCanonicalRevision
                || observedRevision != emittedCanonicalRevision) throw new IllegalArgumentException("replica observation is stale or forged");
        if (!observedFingerprint.equals(fingerprint) || !observedProvenance.equals(provenance)) {
            PhysicalReplicaConflictReason reason = !observedFingerprint.equals(fingerprint) && !observedProvenance.equals(provenance)
                    ? PhysicalReplicaConflictReason.FINGERPRINT_AND_PROVENANCE_MISMATCH
                    : !observedFingerprint.equals(fingerprint) ? PhysicalReplicaConflictReason.FINGERPRINT_MISMATCH
                    : PhysicalReplicaConflictReason.PROVENANCE_MISMATCH;
            return new PhysicalReplicaRecord(objectId, semanticKind, emittedCanonicalRevision, observedRevision, replicaRevision + 1, fingerprint, provenance,
                    PhysicalReplicaState.CONFLICT, Optional.of(observedFingerprint), Optional.of(observedProvenance), Optional.of(reason));
        }
        return new PhysicalReplicaRecord(objectId, semanticKind, emittedCanonicalRevision, observedRevision, replicaRevision + 1, fingerprint, provenance,
                PhysicalReplicaState.OBSERVED_CURRENT, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Retains actual evidence found after a released scope without advancing its expected projection. */
    public PhysicalReplicaRecord conflict(long expectedCanonicalRevision, long expectedReplicaRevision,
                                          String actualFingerprint, String actualProvenance) {
        Objects.requireNonNull(actualFingerprint, "actual fingerprint"); Objects.requireNonNull(actualProvenance, "actual provenance");
        if (state != PhysicalReplicaState.OBSERVED_CURRENT || emittedCanonicalRevision != expectedCanonicalRevision
                || replicaRevision != expectedReplicaRevision || actualFingerprint.isBlank() || actualProvenance.isBlank()) {
            throw new IllegalArgumentException("released replica conflict fence is stale or invalid");
        }
        PhysicalReplicaConflictReason reason = !actualFingerprint.equals(fingerprint) && !actualProvenance.equals(provenance)
                ? PhysicalReplicaConflictReason.FINGERPRINT_AND_PROVENANCE_MISMATCH
                : !actualFingerprint.equals(fingerprint) ? PhysicalReplicaConflictReason.FINGERPRINT_MISMATCH
                : PhysicalReplicaConflictReason.PROVENANCE_MISMATCH;
        return new PhysicalReplicaRecord(objectId, semanticKind, emittedCanonicalRevision, observedCanonicalRevision, replicaRevision + 1,
                fingerprint, provenance, PhysicalReplicaState.CONFLICT, Optional.of(actualFingerprint), Optional.of(actualProvenance), Optional.of(reason));
    }
}
