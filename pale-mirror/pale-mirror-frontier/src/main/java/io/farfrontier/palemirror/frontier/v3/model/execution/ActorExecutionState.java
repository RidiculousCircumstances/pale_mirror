package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Sole retained activity-authority register. No body, resource, route or family progress here. */
public final class ActorExecutionState {
    public static final int MAX_ACTORS = 4_096;
    private final Map<SubjectId, ActorExecution> actors;
    private final Map<ActorActivityKind, Map<SubjectId, ActorExecutionId>> currentByKind;
    private final java.util.List<ActorExecutionId> suspended;
    public ActorExecutionState(Map<SubjectId, ActorExecution> actors) {
        this.actors = Map.copyOf(Objects.requireNonNull(actors, "actor execution records"));
        if (this.actors.size() > MAX_ACTORS) throw new IllegalArgumentException("actor execution retention bound exceeded");
        var current = new java.util.EnumMap<ActorActivityKind, Map<SubjectId, ActorExecutionId>>(ActorActivityKind.class);
        var continuations = new java.util.ArrayList<ActorExecutionId>();
        this.actors.forEach((actor, execution) -> {
            if (!actor.equals(execution.actorId())) throw new IllegalArgumentException("execution index has foreign actor");
            execution.current().ifPresent(id -> current.computeIfAbsent(id.activityKind(), ignored -> new LinkedHashMap<>()).put(actor, id));
            execution.suspended().ifPresent(continuations::add);
        });
        current.replaceAll((kind, values) -> Map.copyOf(values));
        currentByKind = Map.copyOf(current);
        suspended = java.util.List.copyOf(continuations);
    }
    public Map<SubjectId, ActorExecution> actors() { return actors; }
    /** Derived immutable indices are rebuilt only on execution transitions, never per world tick. */
    public Map<SubjectId, ActorExecutionId> current(ActorActivityKind kind) {
        return currentByKind.getOrDefault(Objects.requireNonNull(kind, "activity kind"), Map.of());
    }
    public java.util.Set<ActorActivityKind> currentKinds() { return currentByKind.keySet(); }
    public boolean owns(SubjectId actor, ActorActivityKind kind, SubjectId owner) {
        var id = current(kind).get(Objects.requireNonNull(actor));
        return id != null && id.activityOwnerId().equals(Objects.requireNonNull(owner));
    }
    public java.util.List<ActorExecutionId> suspended() { return suspended; }
    @Override public boolean equals(Object other) { return other instanceof ActorExecutionState state && actors.equals(state.actors); }
    @Override public int hashCode() { return actors.hashCode(); }
    @Override public String toString() { return "ActorExecutionState[actors=" + actors + "]"; }
    public static ActorExecutionState empty() { return new ActorExecutionState(Map.of()); }
    public long generation(SubjectId actorId) {
        ActorExecution execution = actors.get(Objects.requireNonNull(actorId, "actor"));
        return execution == null ? 0L : execution.generation();
    }
    public ActorExecutionId next(SubjectId actorId, ActorActivityKind kind, SubjectId ownerId) {
        return new ActorExecutionId(actorId, kind, ownerId, Math.addExact(generation(actorId), 1L));
    }
    public void requireCurrent(ActorExecutionId id) {
        Objects.requireNonNull(id, "execution identity");
        ActorExecution execution = actors.get(id.actorId());
        if (execution == null || !execution.current().equals(Optional.of(id)))
            throw new IllegalArgumentException("execution authority is absent, stale or foreign");
    }
    public ActorExecutionState begin(ActorExecutionId id, long expectedGeneration) {
        Objects.requireNonNull(id, "execution identity");
        ActorExecution previous = actors.get(id.actorId());
        if (generation(id.actorId()) != expectedGeneration || id.generation() != Math.addExact(expectedGeneration, 1L)
                || previous != null && previous.current().isPresent())
            throw new IllegalArgumentException("execution start lacks exact vacant authority");
        return put(new ActorExecution(id.actorId(), id.generation(), Optional.of(id),
                previous == null ? Optional.empty() : previous.suspended()));
    }
    public ActorExecutionState finish(ActorExecutionId id) {
        requireCurrent(id);
        ActorExecution previous = actors.get(id.actorId());
        return put(new ActorExecution(id.actorId(), previous.generation(), Optional.empty(), previous.suspended()));
    }
    ActorExecutionState suspendAndBegin(ActorExecutionId current, ActorExecutionId successor) {
        requireCurrent(current);
        ActorExecution previous = actors.get(current.actorId());
        if (previous.suspended().isPresent() || !current.actorId().equals(successor.actorId())
                || successor.generation() != Math.addExact(previous.generation(), 1L))
            throw new IllegalArgumentException("interruption requires one exact bounded continuation and successor");
        return put(new ActorExecution(current.actorId(), successor.generation(), Optional.of(successor), Optional.of(current)));
    }
    ActorExecutionState resume(ActorExecutionId suspended, ActorExecutionId successor) {
        ActorExecution previous = actors.get(suspended.actorId());
        if (previous == null || previous.current().isPresent() || !previous.suspended().equals(Optional.of(suspended))
                || !suspended.actorId().equals(successor.actorId()) || suspended.activityKind() != successor.activityKind()
                || !suspended.activityOwnerId().equals(successor.activityOwnerId())
                || successor.generation() != Math.addExact(previous.generation(), 1L))
            throw new IllegalArgumentException("resume lacks the exact vacant continuation authority");
        return put(new ActorExecution(successor.actorId(), successor.generation(), Optional.of(successor), Optional.empty()));
    }
    ActorExecutionState retireSuspended(ActorExecutionId suspended) {
        ActorExecution previous = actors.get(suspended.actorId());
        if (previous == null || !previous.suspended().equals(Optional.of(suspended)))
            throw new IllegalArgumentException("retirement has a foreign suspended execution");
        return put(new ActorExecution(previous.actorId(), previous.generation(), previous.current(), Optional.empty()));
    }
    private ActorExecutionState put(ActorExecution execution) {
        var next = new LinkedHashMap<>(actors);
        next.put(execution.actorId(), execution);
        return new ActorExecutionState(next);
    }
}
