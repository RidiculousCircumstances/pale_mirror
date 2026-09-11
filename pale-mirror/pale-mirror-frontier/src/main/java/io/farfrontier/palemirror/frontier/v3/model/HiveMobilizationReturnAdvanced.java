package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One observed COLD/HOT-safe cursor advance of a survivor's retained homeward route. */
public record HiveMobilizationReturnAdvanced(SubjectId mobilizationId, SubjectId bioformId,
                                             int expectedCursor) implements FrontierPayload {
    public HiveMobilizationReturnAdvanced {
        mobilizationId = Objects.requireNonNull(mobilizationId, "hive return mobilization");
        bioformId = Objects.requireNonNull(bioformId, "hive return bioform");
        if (expectedCursor < 0 || expectedCursor >= TraversalTopology.MAX_NODES) {
            throw new IllegalArgumentException("hive return expected cursor is outside the bounded topology");
        }
    }
    @Override public String type() { return "frontier.hive_mobilization_return_advanced"; }
}
