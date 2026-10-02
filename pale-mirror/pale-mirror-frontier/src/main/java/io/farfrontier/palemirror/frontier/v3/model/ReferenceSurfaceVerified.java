package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Fresh read-only evidence for one isolated surface; never permission to overwrite slots. */
public record ReferenceSurfaceVerified(SubjectId containerId, long expectedCanonicalRevision,
                                       long expectedReplicaRevision, long expectedSurfaceEpoch,
                                       String fingerprint, String provenance) implements FrontierPayload {
    public ReferenceSurfaceVerified {
        Objects.requireNonNull(containerId); Objects.requireNonNull(fingerprint); Objects.requireNonNull(provenance);
        if (expectedCanonicalRevision < 0 || expectedReplicaRevision < 1 || expectedSurfaceEpoch < 1
                || fingerprint.isBlank() || provenance.isBlank())
            throw new IllegalArgumentException("reference surface verification needs exact current evidence");
    }
    @Override public String type() { return "frontier.reference_surface_verified"; }
}
