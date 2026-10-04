package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** One retained COLD cursor advance for one exact member of a hive task assembly. */
public record HiveMobilizationAssemblyAdvanced(SubjectId mobilizationId, SubjectId bioformId, HiveTraversalStep step,
        io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution) implements FrontierPayload {
    /** Initial retained COLD corridor only; production uses the captured step constructor. */
    public HiveMobilizationAssemblyAdvanced(SubjectId mobilizationId, SubjectId bioformId, int expectedCursor,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId execution) {
        this(mobilizationId, bioformId, new HiveTraversalStep(expectedCursor, 1L, -1, java.util.Optional.empty()), execution);
    }
    public int expectedCursor() { return step.expectedCursor(); }
    public HiveMobilizationAssemblyAdvanced {
        mobilizationId = Objects.requireNonNull(mobilizationId, "hive assembly mobilization");
        bioformId = Objects.requireNonNull(bioformId, "hive assembly bioform");
        HiveAssemblyExecutionAuthority.requireDeclaration(Objects.requireNonNull(execution), mobilizationId, bioformId);
        Objects.requireNonNull(step);
        if (step.hotArrival().isPresent() && !step.hotArrival().orElseThrow().body().actorId().equals(bioformId))
            throw new IllegalArgumentException("hive arrival declares a foreign body");
    }
    @Override public String type() { return "frontier.hive_mobilization_assembly_advanced"; }
}
