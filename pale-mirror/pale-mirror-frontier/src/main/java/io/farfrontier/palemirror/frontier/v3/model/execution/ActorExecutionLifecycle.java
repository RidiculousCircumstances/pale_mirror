package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldStateUpdate;
import java.util.Objects;

/** Exclusive transitions; strategies own checkpoints and labour, callers own successor data. */
public final class ActorExecutionLifecycle {
    private final ActorActivityCapabilities capabilities;
    public ActorExecutionLifecycle(ActorActivityCapabilities capabilities) {
        this.capabilities = Objects.requireNonNull(capabilities);
    }
    /** Minted only after the exact owner has acknowledged its checkpoint. */
    public static final class Transition {
        private final FrontierWorldState basis;
        private final FrontierWorldState prepared;
        private final ActorExecutionState executions;
        private Transition(FrontierWorldState basis, FrontierWorldState prepared, ActorExecutionState executions) {
            this.basis = basis; this.prepared = prepared; this.executions = executions;
        }
        /** Family data and authority change in one reference-closed immutable update. */
        public FrontierWorldState commit(FrontierWorldState current, FrontierWorldStateUpdate ownedUpdate) {
            if (current != basis) throw new IllegalArgumentException("prepared execution transition has a stale immutable basis");
            return prepared.withChanges(Objects.requireNonNull(ownedUpdate).actorExecutions(executions));
        }
    }
    public Transition prepareVacant(FrontierWorldState state, ActorExecutionId successor) {
        capabilities.require(successor.activityKind());
        var actor = state.actorLocations().get(successor.actorId());
        var retained = state.actorExecutions().actors().get(successor.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || retained != null && retained.suspended().isPresent())
            throw new IllegalArgumentException("new work admission cannot replace a current or suspended owner");
        if (retained != null && retained.current().isPresent()) {
            var current = retained.current().orElseThrow();
            var capability = capabilities.require(current.activityKind());
            if (capability.interruption() != ActorActivityCapability.Interruption.RELEASE)
                throw new IllegalArgumentException("new work admission cannot replace an unreleased owner");
            return releaseAndBegin(state, current, successor, capability);
        }
        return new Transition(state, state, state.actorExecutions().begin(successor, successor.generation() - 1L));
    }
    public Transition prepareBegin(FrontierWorldState state, ActorExecutionId successor, long atTick) {
        capabilities.require(successor.activityKind());
        var actor = state.actorLocations().get(successor.actorId());
        if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE || atTick < 0)
            throw new IllegalArgumentException("execution admission requires one living declared actor and canonical time");
        if (successor.generation() != Math.addExact(state.actorExecutions().generation(successor.actorId()), 1L))
            throw new IllegalArgumentException("execution admission has a stale generation");
        var retained = state.actorExecutions().actors().get(successor.actorId());
        if (retained == null || retained.current().isEmpty())
            return new Transition(state, state, state.actorExecutions().begin(successor, successor.generation() - 1L));
        var current = retained.current().orElseThrow();
        var capability = capabilities.require(current.activityKind());
        if (capability.interruption() == ActorActivityCapability.Interruption.RELEASE)
            return releaseAndBegin(state, current, successor, capability);
        capability.validateReference(state, current);
        var checkpoint = Objects.requireNonNull(capability.checkpoint(state, current));
        checkpoint.validate(state, current);
        if (!capability.supportsContinuation() || !checkpoint.ready() || retained.suspended().isPresent())
            throw new IllegalArgumentException("execution owner has not released a safe bounded continuation");
        var paused = Objects.requireNonNull(capability.pause(state, current, atTick));
        capability.validateReference(paused, current);
        if (!paused.actorExecutions().equals(state.actorExecutions()))
            throw new IllegalArgumentException("family pause changed common execution authority");
        return new Transition(state, paused, paused.actorExecutions().suspendAndBegin(current, successor));
    }
    private Transition releaseAndBegin(FrontierWorldState state, ActorExecutionId current,
                                        ActorExecutionId successor, ActorActivityCapability capability) {
        capability.validateReference(state, current);
        var checkpoint = Objects.requireNonNull(capability.checkpoint(state, current));
        checkpoint.validate(state, current);
        if (!checkpoint.ready()) throw new IllegalArgumentException("release owner has unfinished obligations");
        var released = Objects.requireNonNull(capability.release(state, current));
        if (!released.actorExecutions().equals(state.actorExecutions()))
            throw new IllegalArgumentException("family release changed common execution authority");
        var executions = released.actorExecutions().finish(current).begin(successor, current.generation());
        return new Transition(state, released, executions);
    }
    public Transition prepareResume(FrontierWorldState state, ActorExecutionId suspended,
                                    ActorExecutionId successor, long atTick) {
        var retained = state.actorExecutions().actors().get(suspended.actorId());
        var actor = state.actorLocations().get(suspended.actorId());
        if (atTick < 0 || retained == null || retained.current().isPresent()
                || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !retained.suspended().equals(java.util.Optional.of(suspended)))
            throw new IllegalArgumentException("resume requires vacant exact suspended authority");
        // Complete successor identity/generation must fail before any owner strategy runs.
        var executions = state.actorExecutions().resume(suspended, successor);
        var capability = capabilities.require(suspended.activityKind());
        if (!capability.supportsContinuation()) throw new IllegalArgumentException("owner cannot resume");
        capability.validateReference(state, suspended);
        var resumed = Objects.requireNonNull(capability.resume(state, suspended, atTick));
        capability.validateReference(resumed, suspended);
        if (!resumed.actorExecutions().equals(state.actorExecutions()))
            throw new IllegalArgumentException("family resume changed common execution authority");
        return new Transition(state, resumed, executions);
    }
    /** The exact family retires either its current or paused claim, never a successor's authority. */
    public ActorExecutionState retire(FrontierWorldState state, SubjectId actor, ActorActivityKind kind, SubjectId owner) {
        return retire(state.actorExecutions(), actor, kind, owner);
    }
    public ActorExecutionState retire(ActorExecutionState executions, SubjectId actor, ActorActivityKind kind, SubjectId owner) {
        capabilities.require(kind);
        Objects.requireNonNull(owner);
        var retained = executions.actors().get(Objects.requireNonNull(actor));
        if (retained == null) throw new IllegalArgumentException("activity retirement has no execution");
        var current = retained.current().filter(id -> id.activityKind() == kind && id.activityOwnerId().equals(owner));
        if (current.isPresent()) return executions.finish(current.orElseThrow());
        var suspended = retained.suspended().filter(id -> id.activityKind() == kind && id.activityOwnerId().equals(owner));
        if (suspended.isEmpty()) throw new IllegalArgumentException("activity retirement has no exact owner authority");
        return executions.retireSuspended(suspended.orElseThrow());
    }
}
