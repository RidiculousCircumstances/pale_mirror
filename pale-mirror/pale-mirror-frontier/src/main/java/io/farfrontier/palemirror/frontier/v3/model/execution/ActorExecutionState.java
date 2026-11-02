package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ImmutableLookupMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Sole retained activity-authority register. No body, resource, route or family progress here. */
public final class ActorExecutionState {
    public static final int MAX_ACTORS = io.farfrontier.palemirror.frontier.v3.model.SimulationCapacity.MAX_ACTORS;
    private final Map<SubjectId, ActorExecution> actors;
    // Derived producer-order ranks are shared across value-only transitions and
    // reconstructed at ingress; they grant no execution authority.
    private final Map<SubjectId, Integer> ranks;
    private final Map<ActorActivityKind, Map<SubjectId, ActorExecutionId>> currentByKind;
    private final java.util.List<ActorExecutionId> suspended;
    public ActorExecutionState(Map<SubjectId, ActorExecution> actors) {
        this.actors = ImmutableLookupMap.copyOf(Objects.requireNonNull(actors, "actor execution records"));
        if (this.actors.size() > MAX_ACTORS) throw new IllegalArgumentException("actor execution retention bound exceeded");
        var current = new java.util.EnumMap<ActorActivityKind, Map<SubjectId, ActorExecutionId>>(ActorActivityKind.class);
        var continuations = new java.util.ArrayList<ActorExecutionId>();
        var order = new LinkedHashMap<SubjectId, Integer>();
        this.actors.forEach((actor, execution) -> {
            if (!actor.equals(execution.actorId())) throw new IllegalArgumentException("execution index has foreign actor");
            order.put(actor, order.size());
            execution.current().ifPresent(id -> current.computeIfAbsent(id.activityKind(), ignored -> new LinkedHashMap<>()).put(actor, id));
            continuations.addAll(execution.continuations());
        });
        current.replaceAll((kind, values) -> ImmutableLookupMap.copyOf(values));
        ranks = ImmutableLookupMap.copyOf(order);
        currentByKind = Map.copyOf(current);
        suspended = java.util.List.copyOf(continuations);
    }
    public Map<SubjectId, ActorExecution> actors() { return actors; }
    /** Derived immutable indices change with their exact execution transition, never per world tick. */
    public Map<SubjectId, ActorExecutionId> current(ActorActivityKind kind) {
        return currentByKind.getOrDefault(Objects.requireNonNull(kind, "activity kind"), Map.of());
    }
    public java.util.Set<ActorActivityKind> currentKinds() { return currentByKind.keySet(); }
    public boolean owns(SubjectId actor, ActorActivityKind kind, SubjectId owner) {
        var id = current(kind).get(Objects.requireNonNull(actor));
        return id != null && id.activityOwnerId().equals(Objects.requireNonNull(owner));
    }
    /** Retained assignment is explicit, but this predicate grants no movement or work authority. */
    public boolean retainsSuspended(SubjectId actor, ActorActivityKind kind, SubjectId owner) {
        var execution = actors.get(Objects.requireNonNull(actor));
        Objects.requireNonNull(kind); Objects.requireNonNull(owner);
        return execution != null && execution.continuations().stream().anyMatch(id ->
                id.activityKind() == kind && id.activityOwnerId().equals(owner));
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
    /** Retention validates an exact reference; it never grants current actuation. */
    public void requireRetained(ActorExecutionId id) {
        Objects.requireNonNull(id, "retained execution identity");
        var execution = actors.get(id.actorId());
        if (execution == null || !execution.current().equals(Optional.of(id)) && !execution.continuations().contains(id))
            throw new IllegalArgumentException("retained execution is absent, stale or foreign");
    }
    public ActorExecutionState begin(ActorExecutionId id, long expectedGeneration) {
        Objects.requireNonNull(id, "execution identity");
        ActorExecution previous = actors.get(id.actorId());
        if (generation(id.actorId()) != expectedGeneration || id.generation() != Math.addExact(expectedGeneration, 1L)
                || previous != null && previous.current().isPresent())
            throw new IllegalArgumentException("execution start lacks exact vacant authority");
        return put(new ActorExecution(id.actorId(), id.generation(), Optional.of(id),
                previous == null ? java.util.List.of() : previous.continuations()));
    }
    public ActorExecutionState finish(ActorExecutionId id) {
        requireCurrent(id);
        ActorExecution previous = actors.get(id.actorId());
        return put(new ActorExecution(id.actorId(), previous.generation(), Optional.empty(), previous.continuations()));
    }
    ActorExecutionState suspendAndBegin(ActorExecutionId current, ActorExecutionId successor) {
        requireCurrent(current);
        ActorExecution previous = actors.get(current.actorId());
        if (previous.continuations().size()>=ActorExecution.MAX_CONTINUATIONS || !current.actorId().equals(successor.actorId())
                || successor.generation() != Math.addExact(previous.generation(), 1L))
            throw new IllegalArgumentException("interruption requires one exact bounded continuation and successor");
        var chain=new java.util.ArrayList<ActorExecutionId>();chain.add(current);chain.addAll(previous.continuations());
        return put(new ActorExecution(current.actorId(), successor.generation(), Optional.of(successor), chain));
    }
    ActorExecutionState resume(ActorExecutionId suspended, ActorExecutionId successor) {
        ActorExecution previous = actors.get(suspended.actorId());
        if (previous == null || previous.current().isPresent() || !previous.suspended().equals(Optional.of(suspended))
                || !suspended.actorId().equals(successor.actorId()) || suspended.activityKind() != successor.activityKind()
                || !suspended.activityOwnerId().equals(successor.activityOwnerId())
                || successor.generation() != Math.addExact(previous.generation(), 1L))
            throw new IllegalArgumentException("resume lacks the exact vacant continuation authority");
        return put(new ActorExecution(successor.actorId(), successor.generation(), Optional.of(successor), previous.continuations().subList(1,previous.continuations().size())));
    }
    ActorExecutionState retireSuspended(ActorExecutionId suspended) {
        ActorExecution previous = actors.get(suspended.actorId());
        if (previous == null || !previous.continuations().contains(suspended))
            throw new IllegalArgumentException("retirement has a foreign suspended execution");
        var chain=new java.util.ArrayList<>(previous.continuations());chain.remove(suspended);
        return put(new ActorExecution(previous.actorId(), previous.generation(), previous.current(), chain));
    }
    private ActorExecutionState(ActorExecutionState previous, ActorExecution execution) {
        var actor = execution.actorId();
        var before = previous.actors.get(actor);
        actors = ImmutableLookupMap.withEntry(previous.actors, actor, execution);
        if (actors.size() > MAX_ACTORS) throw new IllegalArgumentException("actor execution retention bound exceeded");
        ranks = before == null ? ImmutableLookupMap.withEntry(previous.ranks, actor, previous.ranks.size()) : previous.ranks;
        var prior = before == null ? Optional.<ActorExecutionId>empty() : before.current();
        var next = execution.current();
        if (prior.equals(next)) currentByKind = previous.currentByKind;
        else {
            var current = new java.util.EnumMap<ActorActivityKind, Map<SubjectId, ActorExecutionId>>(ActorActivityKind.class);
            current.putAll(previous.currentByKind);
            if (prior.isPresent() && next.isPresent() && prior.orElseThrow().activityKind() == next.orElseThrow().activityKind()) {
                var id = next.orElseThrow();
                current.put(id.activityKind(), ImmutableLookupMap.withEntry(current.get(id.activityKind()), actor, id));
            } else {
                if (prior.isPresent()) {
                    var kind = prior.orElseThrow().activityKind();
                    var remaining = ImmutableLookupMap.withoutEntry(current.get(kind), actor);
                    if (remaining.isEmpty()) current.remove(kind); else current.put(kind, remaining);
                }
                next.ifPresent(id -> current.put(id.activityKind(), insertOrdered(
                        current.getOrDefault(id.activityKind(), Map.of()), actor, id)));
            }
            currentByKind = Map.copyOf(current);
        }
        var paused=before==null?java.util.List.<ActorExecutionId>of():before.continuations();
        if(paused.equals(execution.continuations()))suspended=previous.suspended;
        else{
            var chain=new java.util.ArrayList<>(previous.suspended);chain.removeAll(paused);
            int at=0;while(at<chain.size() && ranks.get(chain.get(at).actorId())<ranks.get(actor))at++;
            chain.addAll(at,execution.continuations());suspended=java.util.List.copyOf(chain);
        }
    }

    private Map<SubjectId, ActorExecutionId> insertOrdered(Map<SubjectId, ActorExecutionId> values,
                                                          SubjectId actor, ActorExecutionId execution) {
        var result = new LinkedHashMap<SubjectId, ActorExecutionId>(); boolean inserted = false;
        int rank = ranks.get(actor);
        for (var entry : values.entrySet()) {
            if (!inserted && ranks.get(entry.getKey()) > rank) { result.put(actor, execution); inserted = true; }
            result.put(entry.getKey(), entry.getValue());
        }
        if (!inserted) result.put(actor, execution);
        return ImmutableLookupMap.copyOf(result);
    }
    private ActorExecutionState put(ActorExecution execution) { return new ActorExecutionState(this, execution); }
}
