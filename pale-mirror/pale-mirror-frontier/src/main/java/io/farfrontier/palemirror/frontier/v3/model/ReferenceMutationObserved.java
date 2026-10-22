package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** Complete observed successor under the SAME physical owner, not a release or a projection request. */
public record ReferenceMutationObserved(SubjectId containerId, long authorityEpoch,
        long expectedCanonicalRevision, long expectedReplicaRevision, long successorRevision,
        String fingerprint) implements FrontierPayload {
    public ReferenceMutationObserved {
        Objects.requireNonNull(containerId); Objects.requireNonNull(fingerprint);
        if (authorityEpoch < 1 || expectedCanonicalRevision < 0 || expectedReplicaRevision < 1
                || successorRevision <= expectedCanonicalRevision || fingerprint.isBlank())
            throw new IllegalArgumentException("observed mutation lacks its exact predecessor and successor");
    }
    @Override public String type() { return "frontier.reference_mutation_observed"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
