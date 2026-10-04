package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import java.util.Objects;

/** Owner-local spatial settlement before the common body relinquishes custody.
 * This is neither an activity interruption nor evidence of semantic arrival. */
@FunctionalInterface
public interface ActorActivityBodyCheckpoint {
    record Request(FrontierWorldState expectedState, ActorExecutionId execution,
                   ActorBodyId body, BodyPosition observedPosition) {
        public Request {
            Objects.requireNonNull(expectedState); Objects.requireNonNull(execution);
            Objects.requireNonNull(body); Objects.requireNonNull(observedPosition);
            if (!execution.actorId().equals(body.actorId()))
                throw new IllegalArgumentException("body checkpoint declares a foreign execution actor");
        }
    }
    record Acknowledgement(Request request, FrontierWorldStateUpdate changes) {
        public Acknowledgement { Objects.requireNonNull(request); Objects.requireNonNull(changes); }
    }
    Acknowledgement acknowledge(Request request);

    /** Explicit owner declaration: its goals/effects carry no competing current pose.
     * Registration must supply this deliberately; it is never a default capability. */
    static ActorActivityBodyCheckpoint usesActorLocation() {
        return request -> new Acknowledgement(request, FrontierWorldStateUpdate.begin());
    }
}
