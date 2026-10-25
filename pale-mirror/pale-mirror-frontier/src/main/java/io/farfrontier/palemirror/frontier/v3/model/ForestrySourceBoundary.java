package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.model.forestry.TreeReference;
import java.util.Objects;

/** Exact whole-object custody fence. Preparing a native image is not evidence of its application. */
public record ForestrySourceBoundary(TreeReference tree, Operation operation, long expectedEpoch,
        long expectedReplicaRevision, String fingerprint) implements FrontierPayload {
    public static final String TYPE = "frontier.forestry_source_boundary.v1";
    public enum Operation {
        PREPARE(1), CONFIRM(2), RELEASE(3), WITHDRAW_PROJECTION(4);
        private final int tag; Operation(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Operation decode(int tag) {
            for (var value : values()) if (value.tag == tag) return value;
            throw new IllegalArgumentException("unknown forest source boundary operation");
        }
    }
    public ForestrySourceBoundary {
        Objects.requireNonNull(tree); Objects.requireNonNull(operation); Objects.requireNonNull(fingerprint);
        if (expectedEpoch < 0 || expectedReplicaRevision < 0 || fingerprint.isBlank()
                || operation != Operation.PREPARE && (expectedEpoch < 1 || expectedReplicaRevision < 1))
            throw new IllegalArgumentException("forest source boundary lacks its exact custody fence");
    }
    @Override public String type() { return TYPE; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
