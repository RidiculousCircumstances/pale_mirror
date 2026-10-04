package io.farfrontier.palemirror.frontier.v3.model.execution;

import java.util.List;
import java.util.Objects;

/** Captured physical incarnations paired with exact executions; no family or goal inference. */
public record ActorActuationGroup(List<ActorActuationId> members) {
    public ActorActuationGroup {
        members = List.copyOf(Objects.requireNonNull(members, "captured group actuations"));
        if (members.isEmpty() || members.size() > ActorExecutionState.MAX_ACTORS
                || members.stream().map(value -> value.body().actorId()).distinct().count() != members.size())
            throw new IllegalArgumentException("actuation group requires bounded distinct actors");
    }
    public ActorExecutionGroup executions() { return new ActorExecutionGroup(members.stream().map(ActorActuationId::execution).toList()); }
}
