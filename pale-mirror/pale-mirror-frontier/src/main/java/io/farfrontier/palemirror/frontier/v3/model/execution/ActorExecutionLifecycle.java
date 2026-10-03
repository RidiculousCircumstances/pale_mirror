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
    /** A group's job and all participant authorities enter one immutable, reference-closed update. */
    public Transition prepareVacantGroup(FrontierWorldState state, ActorExecutionGroup successor) {
        Objects.requireNonNull(successor, "successor executions");
        FrontierWorldState prepared = state;
        ActorExecutionState executions = state.actorExecutions();
        for (var id : successor.members()) {
            capabilities.require(id.activityKind());
            var actor = prepared.actorLocations().get(id.actorId());
            var retained = executions.actors().get(id.actorId());
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                    || retained != null && retained.suspended().isPresent())
                throw new IllegalArgumentException("group cannot replace an unavailable participant");
            if (retained != null && retained.current().isPresent()) {
                var current = retained.current().orElseThrow();
                var capability = capabilities.require(current.activityKind());
                if (capability.interruption() != ActorActivityCapability.Interruption.RELEASE)
                    throw new IllegalArgumentException("group participant has an unreleased owner");
                capability.validateReference(prepared, current);
                var checkpoint = Objects.requireNonNull(capability.checkpoint(prepared, current));
                checkpoint.validate(prepared, current);
                if (!checkpoint.ready()) throw new IllegalArgumentException("group participant has unfinished effects");
                prepared = Objects.requireNonNull(capability.release(prepared, current));
                if (!prepared.actorExecutions().equals(state.actorExecutions()))
                    throw new IllegalArgumentException("group release changed common execution authority");
                executions = executions.finish(current);
            }
            executions = executions.begin(id, executions.generation(id.actorId()));
        }
        return new Transition(state, prepared, executions);
    }
    /** A terminal-only family acknowledges its completed group before a new purpose can begin. */
    public Transition prepareTerminalGroupReplacement(FrontierWorldState state, ActorExecutionGroup completed,
                                                       ActorExecutionGroup successor) {
        Objects.requireNonNull(completed); Objects.requireNonNull(successor);
        var actors = completed.members().stream().map(ActorExecutionId::actorId).collect(java.util.stream.Collectors.toSet());
        if (!actors.equals(successor.members().stream().map(ActorExecutionId::actorId).collect(java.util.stream.Collectors.toSet())))
            throw new IllegalArgumentException("group replacement must retain its exact actor roster");
        var executions = state.actorExecutions();
        for (var current : completed.members()) {
            executions.requireCurrent(current);
            var capability = capabilities.require(current.activityKind());
            capability.validateReference(state, current);
            var checkpoint = Objects.requireNonNull(capability.checkpoint(state, current));
            checkpoint.validate(state, current);
            if (capability.interruption() != ActorActivityCapability.Interruption.TERMINAL_ONLY || !checkpoint.ready())
                throw new IllegalArgumentException("group owner has not acknowledged its terminal boundary");
            executions = executions.finish(current);
        }
        for (var next : successor.members()) {
            capabilities.require(next.activityKind());
            var actor = state.actorLocations().get(next.actorId());
            if (actor == null || actor.condition().status() != ActorLifeStatus.ALIVE)
                throw new IllegalArgumentException("group replacement requires living exact actors");
            executions = executions.begin(next, executions.generation(next.actorId()));
        }
        return new Transition(state, state, executions);
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
    /** Release-only owners have no retained work; other families acknowledge death separately. */
    public Transition preparePassiveDeath(FrontierWorldState state, SubjectId actor, ActorExecutionState ownedRetirements) {
        Objects.requireNonNull(ownedRetirements, "owner death retirements");
        var retained = ownedRetirements.actors().get(Objects.requireNonNull(actor));
        if (retained == null || retained.current().isEmpty())
            return new Transition(state, state, ownedRetirements);
        var current = retained.current().orElseThrow();
        var capability = capabilities.require(current.activityKind());
        if (capability.interruption() != ActorActivityCapability.Interruption.RELEASE)
            return new Transition(state, state, ownedRetirements);
        state.actorExecutions().requireCurrent(current);
        capability.validateReference(state, current);
        var checkpoint = Objects.requireNonNull(capability.checkpoint(state, current));
        checkpoint.validate(state, current);
        if (!checkpoint.ready()) throw new IllegalArgumentException("passive death needs its owner effect settlement");
        var released = Objects.requireNonNull(capability.release(state, current));
        if (!released.actorExecutions().equals(state.actorExecutions()))
            throw new IllegalArgumentException("family death release changed common authority");
        return new Transition(state, released, ownedRetirements.finish(current));
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
