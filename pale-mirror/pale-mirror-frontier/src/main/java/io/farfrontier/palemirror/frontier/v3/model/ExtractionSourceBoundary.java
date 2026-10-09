package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.*;
import io.farfrontier.palemirror.frontier.v3.model.extraction.ExtractionRegion;
import java.util.Objects;

/** Exact source custody lifecycle. Prepared is authority, not proof that projection happened. */
public record ExtractionSourceBoundary(ExtractionRegion region, Operation operation, long expectedEpoch,
        long expectedReplicaRevision, String fingerprint) implements FrontierPayload {
    public enum Operation {
        PREPARE(1), CONFIRM(2), RELEASE(3);
        private final int tag; Operation(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Operation decode(int tag) {
            return switch (tag) { case 1 -> PREPARE; case 2 -> CONFIRM; case 3 -> RELEASE;
                default -> throw new IllegalArgumentException("unknown source boundary operation"); };
        }
    }
    public ExtractionSourceBoundary {
        Objects.requireNonNull(region); Objects.requireNonNull(operation); Objects.requireNonNull(fingerprint);
        if (expectedEpoch < 0 || expectedReplicaRevision < 0 || fingerprint.isBlank()
                || operation != Operation.PREPARE && (expectedEpoch < 1 || expectedReplicaRevision < 1))
            throw new IllegalArgumentException("source boundary lacks its exact evidence fence");
    }
    @Override public String type() { return "frontier.extraction_source_boundary"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
