package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import java.util.Objects;

/** Owner-local retained-reference rebinding, committed atomically with the common successor. */
@FunctionalInterface
public interface ActorActivityResumption {
    record Request(FrontierWorldState expectedState, ActorExecutionId suspended, ActorExecutionId successor) {
        public Request {
            Objects.requireNonNull(expectedState); Objects.requireNonNull(suspended); Objects.requireNonNull(successor);
            if (!suspended.actorId().equals(successor.actorId()) || suspended.activityKind() != successor.activityKind()
                    || !suspended.activityOwnerId().equals(successor.activityOwnerId()) || successor.generation() <= suspended.generation())
                throw new IllegalArgumentException("resumption must retain actor, purpose and owner under a newer generation");
        }
    }
    record Acknowledgement(Request request, FrontierWorldStateUpdate changes) {
        public Acknowledgement { Objects.requireNonNull(request); Objects.requireNonNull(changes); }
    }
    Acknowledgement acknowledge(Request request);
    /** Explicit registration for an owner whose record retains actor/job, not an execution generation. */
    static ActorActivityResumption noRetainedExecutionReference() {
        return request -> new Acknowledgement(request, FrontierWorldStateUpdate.begin());
    }
}
