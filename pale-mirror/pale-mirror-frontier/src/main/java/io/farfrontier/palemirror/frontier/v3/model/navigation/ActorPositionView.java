package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.BodyPosition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;

/** Read-only as-of positions. An observation cannot award arrival, custody or execution authority. */
@FunctionalInterface
public interface ActorPositionView {
    BodyPosition bodyAt(SubjectId actorId);

    static ActorPositionView canonical(FrontierWorldState state, long tick) {
        return actorId -> ActorMovementProjection.bodyAt(state, actorId, tick);
    }
}
