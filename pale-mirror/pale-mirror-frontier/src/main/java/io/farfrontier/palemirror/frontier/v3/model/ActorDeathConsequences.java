package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionState;
import java.util.Objects;

/** Domain participants settle their obligations; the physical owner knows no family stages. */
public interface ActorDeathConsequences {
    record Settlement(FrontierWorldState expectedState, FrontierWorldStateUpdate changes,
                       ActorExecutionState executions) {
        public Settlement {
            Objects.requireNonNull(expectedState); Objects.requireNonNull(changes); Objects.requireNonNull(executions);
        }
    }
    Settlement settle(FrontierWorldState state, SubjectId actor, long atTick);
}
