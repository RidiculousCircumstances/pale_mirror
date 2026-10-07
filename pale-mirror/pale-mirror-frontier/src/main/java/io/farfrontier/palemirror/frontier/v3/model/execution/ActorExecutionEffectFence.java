package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Optional;

/** A composed physical-effect owner may prevent ordinary authority replacement, never settle death. */
@FunctionalInterface
public interface ActorExecutionEffectFence {
    Optional<SubjectId> pendingOwner(FrontierWorldState state, ActorExecutionId execution);
}
