package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.Objects;

/** Captured activity/body permission; a family receipt never installs the observed pose. */
public record ActorHotObservation(ActorActuationId actuation, long scopeRevision) {
    public ActorHotObservation {
        Objects.requireNonNull(actuation, "HOT observation actuation");
        if (scopeRevision < 1) throw new IllegalArgumentException("HOT observation needs an exact scope version");
    }

    /** The owning family supplies its declared execution and scope, not a guessed dispatch key. */
    public void require(FrontierWorldState state, ActorExecutionId expectedExecution,
                        long expectedScopeRevision, BodyPosition observedBody) {
        if (!actuation.execution().equals(expectedExecution) || scopeRevision != expectedScopeRevision)
            throw new IllegalArgumentException("HOT observation has stale or foreign execution/scope");
        ActorBodyAuthority.requireActuation(state, actuation);
        if (!state.actorLocations().get(actuation.body().actorId()).body().equals(observedBody))
            throw new IllegalArgumentException("HOT family receipt lacks independently inspected body");
    }
}
