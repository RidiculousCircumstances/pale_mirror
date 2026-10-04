package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One observed COLD/HOT-safe cursor advance of a survivor's retained homeward route. */
public record HiveMobilizationReturnAdvanced(SubjectId mobilizationId, SubjectId bioformId,
                                             HiveTraversalStep step,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution) implements FrontierPayload {
    /** Initial retained COLD corridor only; production uses the captured step constructor. */
    public HiveMobilizationReturnAdvanced(SubjectId mobilizationId, SubjectId bioformId, int expectedCursor,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution) {
        this(mobilizationId, bioformId, new HiveTraversalStep(expectedCursor, 1L, -1, java.util.Optional.empty()), execution);
    }
    public int expectedCursor() { return step.expectedCursor(); }
    public HiveMobilizationReturnAdvanced {
        mobilizationId = Objects.requireNonNull(mobilizationId, "hive return mobilization");
        bioformId = Objects.requireNonNull(bioformId, "hive return bioform");
        HiveReturnExecutionAuthority.requireDeclaration(Objects.requireNonNull(execution), mobilizationId, bioformId);
        Objects.requireNonNull(step);
        if (step.hotArrival().isPresent() && !step.hotArrival().orElseThrow().body().actorId().equals(bioformId))
            throw new IllegalArgumentException("hive arrival declares a foreign body");
    }
    @Override public String type() { return "frontier.hive_mobilization_return_advanced"; }
}
