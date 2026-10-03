package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Exact participant authority supplied by a group owner, never reconstructed from a scene. */
public record ActorExecutionGroup(List<ActorExecutionId> members) {
    public ActorExecutionGroup {
        members = List.copyOf(Objects.requireNonNull(members, "group executions"));
        if (members.isEmpty() || members.size() > ActorExecutionState.MAX_ACTORS
                || members.stream().map(ActorExecutionId::actorId).distinct().count() != members.size())
            throw new IllegalArgumentException("execution group requires bounded distinct declared actors");
    }
    public void requireDeclaration(ActorActivityKind kind, SubjectId owner, Collection<SubjectId> actors) {
        Objects.requireNonNull(kind); Objects.requireNonNull(owner); Objects.requireNonNull(actors);
        if (actors.size() != members.size() || !Set.copyOf(actors).equals(
                members.stream().map(ActorExecutionId::actorId).collect(java.util.stream.Collectors.toUnmodifiableSet()))
                || members.stream().anyMatch(id -> id.activityKind() != kind || !id.activityOwnerId().equals(owner)))
            throw new IllegalArgumentException("group execution differs from its declared kind, owner or complete roster");
    }
    public void requireCurrent(ActorExecutionState executions) { members.forEach(executions::requireCurrent); }
}
