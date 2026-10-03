package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.Objects;
import java.util.Optional;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

/** Assessment is bound to exact immutable state and execution, never an ownership release. */
public record ActorActivityCheckpoint(FrontierWorldState basis, ActorExecutionId execution,
                                      Optional<Wait> waiting) {
    public enum Reason { PHYSICAL_OPERATION, OWNER_TERMINAL_BOUNDARY }
    public record Wait(Reason reason, SubjectId dependencyOwner) {
        public Wait { Objects.requireNonNull(reason); Objects.requireNonNull(dependencyOwner); }
    }
    public ActorActivityCheckpoint {
        Objects.requireNonNull(basis); Objects.requireNonNull(execution); Objects.requireNonNull(waiting);
    }
    public boolean ready() { return waiting.isEmpty(); }
    public void validate(FrontierWorldState state, ActorExecutionId expected) {
        if (basis != state || !execution.equals(expected))
            throw new IllegalArgumentException("owner checkpoint has a stale state or foreign execution");
        state.actorExecutions().requireCurrent(expected);
    }
}
