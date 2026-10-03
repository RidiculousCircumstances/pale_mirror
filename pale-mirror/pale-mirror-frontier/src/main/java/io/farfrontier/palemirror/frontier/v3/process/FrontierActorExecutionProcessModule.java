package io.farfrontier.palemirror.frontier.v3.process;

import io.farfrontier.palemirror.frontier.v3.api.FrontierEvent;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import io.farfrontier.palemirror.frontier.v3.model.ActorExecutionComposition;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionResumed;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorPresenceStarted;

/** Shared committed execution transitions; no family progress, body or food dispatch here. */
final class FrontierActorExecutionProcessModule implements FrontierWorldProcessModule {
    @Override public FrontierWorldState reduce(FrontierWorldState state, FrontierEvent event) {
        if (event.payload() instanceof ActorPresenceStarted presence) {
            if (!event.subject().equals(presence.execution().actorId()) || event.instant().ticks() != presence.atTick())
                throw new IllegalArgumentException("presence event has foreign subject or time");
            var retained = state.actorExecutions().actors().get(event.subject());
            if (retained != null && retained.current().isPresent())
                throw new IllegalArgumentException("presence cannot replace an active execution");
            return ActorExecutionComposition.LIFECYCLE.prepareBegin(state, presence.execution(), presence.atTick())
                    .commit(state, FrontierWorldStateUpdate.begin());
        }
        if (!(event.payload() instanceof ActorExecutionResumed resumed)
                || !event.subject().equals(resumed.successor().actorId()) || event.instant().ticks() != resumed.atTick())
            throw new IllegalArgumentException("execution module does not own this exact transition");
        return ActorExecutionComposition.LIFECYCLE.prepareResume(state, resumed.suspended(),
                resumed.successor(), resumed.releasing(), resumed.atTick()).commit(state, FrontierWorldStateUpdate.begin());
    }
}
