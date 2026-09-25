package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Read-only receipt of the current physical depot after a mixed HOT/COLD harvest. */
public record ResourceSiteHarvestDeferredObservation(PhysicalObservationId id, PhysicalIntentId intentId,
                                                     SubjectId siteId, SubjectId jobId, SubjectId containerId,
                                                     long completedGrowthEpoch, long custodyEpoch,
                                                     long emittedCanonicalRevision, long replicaRevision,
                                                     String depotFingerprint, String depotProvenance)
        implements PhysicalEffectObservation {
    public ResourceSiteHarvestDeferredObservation {
        Objects.requireNonNull(id, "deferred harvest observation id");
        Objects.requireNonNull(intentId, "deferred harvest intent");
        Objects.requireNonNull(siteId, "deferred harvest site");
        Objects.requireNonNull(jobId, "deferred harvest job");
        Objects.requireNonNull(containerId, "deferred harvest depot");
        depotFingerprint = Objects.requireNonNull(depotFingerprint, "deferred harvest depot fingerprint");
        depotProvenance = Objects.requireNonNull(depotProvenance, "deferred harvest depot provenance");
        if (!siteId.value().startsWith("site:") || !jobId.value().startsWith("job:site-harvest-")
                || !containerId.value().startsWith("container:") || completedGrowthEpoch < 1
                || custodyEpoch < 1 || emittedCanonicalRevision < 0 || replicaRevision < 1
                || !depotFingerprint.matches("sha256:[0-9a-f]{64}") || depotProvenance.isBlank()) {
            throw new IllegalArgumentException("deferred harvest observation has invalid identity or depot evidence");
        }
    }
}
