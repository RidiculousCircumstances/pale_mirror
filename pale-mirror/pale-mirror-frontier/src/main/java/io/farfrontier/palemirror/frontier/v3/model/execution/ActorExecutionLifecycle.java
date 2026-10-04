package io.farfrontier.palemirror.frontier.v3.model.execution;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ActorLifeStatus;
import io.farfrontier.palemirror.frontier.v3.model.ActorDeathConsequences;
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
        return prepareGroup(state, state.actorExecutions(), successor);
    }
    /** A family acknowledges its old cohort in the same transaction as its successor data.
     * Cohorts may shrink after casualties or grow by explicitly admitted new participants. */
    public Transition prepareAcknowledgedGroupHandoff(FrontierWorldState state, ActorExecutionGroup completed,
                                                       ActorExecutionGroup successor) {
        return prepareGroup(state, retireCurrentGroup(state.actorExecutions(), completed), successor);
    }
    private Transition prepareGroup(FrontierWorldState state, ActorExecutionState executions,
                                    ActorExecutionGroup successor) {
        Objects.requireNonNull(successor, "successor executions");
        FrontierWorldState prepared = state;
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
                                    ActorExecutionId successor, java.util.Optional<ActorExecutionId> releasing, long atTick) {
        Objects.requireNonNull(releasing, "exact current resume predecessor");
        var retained = state.actorExecutions().actors().get(suspended.actorId());
        var actor = state.actorLocations().get(suspended.actorId());
        if (atTick < 0 || retained == null || !retained.current().equals(releasing)
                || actor == null || actor.condition().status() != ActorLifeStatus.ALIVE
                || !retained.suspended().equals(java.util.Optional.of(suspended)))
            throw new IllegalArgumentException("resume requires exact current and suspended authorities");
        // Complete successor identity/generation must fail before any owner strategy runs.
        var vacant = releasing.isEmpty() ? state.actorExecutions()
                : state.actorExecutions().finish(releasing.orElseThrow());
        var executions = vacant.resume(suspended, successor);
        FrontierWorldState prepared = state;
        if (releasing.isPresent()) {
            var current = releasing.orElseThrow();
            var passive = capabilities.require(current.activityKind());
            if (passive.interruption() != ActorActivityCapability.Interruption.RELEASE)
                throw new IllegalArgumentException("resume cannot displace an active owner without acknowledgement");
            passive.validateReference(state, current);
            var checkpoint = Objects.requireNonNull(passive.checkpoint(state, current));
            checkpoint.validate(state, current);
            if (!checkpoint.ready()) throw new IllegalArgumentException("resume predecessor retains unfinished effects");
            prepared = Objects.requireNonNull(passive.release(state, current));
            if (!prepared.actorExecutions().equals(state.actorExecutions()))
                throw new IllegalArgumentException("resume predecessor changed shared execution authority");
        }
        var capability = capabilities.require(suspended.activityKind());
        if (!capability.supportsContinuation()) throw new IllegalArgumentException("owner cannot resume");
        capability.validateReference(prepared, suspended);
        var resumed = Objects.requireNonNull(capability.resume(prepared, suspended, atTick));
        capability.validateReference(resumed, suspended);
        if (!resumed.actorExecutions().equals(state.actorExecutions()))
            throw new IllegalArgumentException("family resume changed common execution authority");
        return new Transition(state, resumed, executions);
    }
    /** Prepare all retained owner checkpoints against the same observed physical fact.
     * The body owner combines these changes with its pose/absence update atomically. */
    public FrontierWorldStateUpdate checkpointBodyDeparture(FrontierWorldState state, ActorBodyId body,
                                                            io.farfrontier.palemirror.frontier.v3.model.BodyPosition position) {
        var binding = io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority.require(state, body);
        if (binding.phase() != io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase.RUNNING
                && binding.phase() != io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase.AMBIGUOUS)
            throw new IllegalArgumentException("body checkpoint requires an actually admitted physical incarnation");
        var changes = FrontierWorldStateUpdate.begin();
        var retained = state.actorExecutions().actors().get(body.actorId());
        if (retained == null) return changes;
        var claims = new java.util.ArrayList<ActorExecutionId>(2);
        retained.current().ifPresent(claims::add);
        retained.suspended().ifPresent(claims::add);
        for (var id : claims) {
            var capability = capabilities.require(id.activityKind());
            capability.validateReference(state, id);
            var request = new ActorActivityBodyCheckpoint.Request(state, id, body, position);
            var result = Objects.requireNonNull(capability.bodyCheckpoint().acknowledge(request));
            if (result.request().expectedState() != state || !result.request().equals(request))
                throw new IllegalArgumentException("body checkpoint owner acknowledged stale or foreign evidence");
            var owned = result.changes();
            if (owned.changedComponents().contains(FrontierWorldStateUpdate.Component.ACTOR_LOCATIONS)
                    || owned.changedComponents().contains(FrontierWorldStateUpdate.Component.FENCED_RECOVERY)
                    || owned.changedComponents().contains(FrontierWorldStateUpdate.Component.ACTOR_EXECUTIONS))
                throw new IllegalArgumentException("body checkpoint owner cannot replace physical or execution authority");
            changes.merge(owned);
        }
        return changes;
    }

    /** Notify exact registered owners even when no scene exists or another purpose is current.
     * The owner may retain unresolved cargo/effects; this does not fabricate completion. */
    public ActorDeathConsequences.Settlement acknowledgeActivityDeath(
            FrontierWorldState state, SubjectId actor, ActorExecutionState ownedRetirements, long atTick) {
        Objects.requireNonNull(actor);
        if (atTick < 0) throw new IllegalArgumentException("death acknowledgement requires canonical time");
        var retained = state.actorExecutions().actors().get(actor);
        var acknowledged = FrontierWorldStateUpdate.begin();
        Objects.requireNonNull(ownedRetirements);
        if (retained == null) return new ActorDeathConsequences.Settlement(state, acknowledged, ownedRetirements);
        var claims = new java.util.ArrayList<ActorExecutionId>(2);
        retained.current().ifPresent(claims::add);
        retained.suspended().ifPresent(claims::add);
        for (var id : claims) {
            var capability = capabilities.require(id.activityKind());
            var death = capability.deathAcknowledgement();
            if (death.isEmpty()) continue; // No immediate family outcome; its causal owner retains terminal settlement.
            capability.validateReference(state, id);
            var result = Objects.requireNonNull(death.orElseThrow().acknowledge(state, id, atTick), "owner death acknowledgement");
            if (result.expectedState() != state || !result.execution().equals(id))
                throw new IllegalArgumentException("activity death acknowledgement has stale or foreign evidence");
            var changes = result.changes();
            if (changes.changedComponents().contains(FrontierWorldStateUpdate.Component.ACTOR_LOCATIONS)
                    || changes.changedComponents().contains(FrontierWorldStateUpdate.Component.FENCED_RECOVERY)
                    || changes.changedComponents().contains(FrontierWorldStateUpdate.Component.ACTOR_EXECUTIONS))
                throw new IllegalArgumentException("activity death cannot replace physical or common execution authority");
            acknowledged.merge(changes);
            if (result.disposition() == ActorActivityDeath.Disposition.RETIRE_EXACT_EXECUTION) {
                ownedRetirements = retained.current().equals(java.util.Optional.of(id))
                        ? ownedRetirements.finish(id) : ownedRetirements.retireSuspended(id);
            } else if (result.disposition() == ActorActivityDeath.Disposition.RETIRE_DECLARED_GROUP) {
                var group = result.retiringGroup().orElseThrow();
                group.requireCurrent(state.actorExecutions());
                for (var member : group.members()) capabilities.require(member.activityKind()).validateReference(state, member);
                ownedRetirements = retireCurrentGroup(ownedRetirements, group);
            }
        }
        return new ActorDeathConsequences.Settlement(state, acknowledged, ownedRetirements);
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

    /** One family-owned terminal outcome retires its complete exact current participant group. */
    public ActorExecutionState retireCurrentGroup(ActorExecutionState executions, ActorExecutionGroup group) {
        group.requireCurrent(executions);
        var result = executions;
        for (var id : group.members()) {
            capabilities.require(id.activityKind());
            result = result.finish(id);
        }
        return result;
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
