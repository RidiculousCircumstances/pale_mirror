package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Producer-declared provider context; a bare ID never selects navigation behavior. */
public sealed interface ActorMovementContext permits ActorMovementContext.ServiceExit {
    record ServiceExit(SubjectId settlementId, SubjectId depotId) implements ActorMovementContext {
        public ServiceExit {
            Objects.requireNonNull(settlementId, "movement settlement");
            Objects.requireNonNull(depotId, "movement service depot");
        }
    }
}
