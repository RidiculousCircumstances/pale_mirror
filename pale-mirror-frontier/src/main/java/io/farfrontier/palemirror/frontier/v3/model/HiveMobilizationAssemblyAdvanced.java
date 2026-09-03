package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One retained COLD cursor advance for one exact member of a hive task assembly. */
public record HiveMobilizationAssemblyAdvanced(SubjectId mobilizationId, SubjectId bioformId, int expectedCursor) implements FrontierPayload {
    public HiveMobilizationAssemblyAdvanced {
        mobilizationId = Objects.requireNonNull(mobilizationId, "hive assembly mobilization");
        bioformId = Objects.requireNonNull(bioformId, "hive assembly bioform");
        if (expectedCursor < 0 || expectedCursor >= TraversalTopology.MAX_NODES) {
            throw new IllegalArgumentException("hive assembly expected cursor is outside the bounded topology");
        }
    }
    @Override public String type() { return "frontier.hive_mobilization_assembly_advanced"; }
}
