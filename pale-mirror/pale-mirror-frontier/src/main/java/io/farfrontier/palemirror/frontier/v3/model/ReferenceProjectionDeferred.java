package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** An unloaded fixed store still contains the exact predecessor: defer presentation, retain canonical stock. */
public record ReferenceProjectionDeferred(SubjectId containerId, long authorityEpoch, long expectedReplicaRevision,
        String fingerprint, String provenance) implements FrontierPayload {
    public ReferenceProjectionDeferred {
        Objects.requireNonNull(containerId); Objects.requireNonNull(fingerprint); Objects.requireNonNull(provenance);
        if (authorityEpoch < 1 || expectedReplicaRevision < 1 || fingerprint.isBlank() || provenance.isBlank())
            throw new IllegalArgumentException("deferred projection requires exact predecessor evidence");
    }
    @Override public String type() { return "frontier.reference_projection_deferred"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }

}
