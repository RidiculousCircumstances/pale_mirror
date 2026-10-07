package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import java.util.Objects;

/**
 * Activity-independent body fencing over the existing single recovery store.
 * No pose, job, movement, resource balance or second physical epoch is retained here.
 * Ordinary activity changes do not call prepare/retire: they leave this authority intact.
 */
public final class ActorBodyAuthority {
    private ActorBodyAuthority() { }

    /** Versioned hydration and pre-WAL closure reject all historical scene-owned body tuples. */
    static void validate(java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ActorLocation> actors,
                         FencedRecoveryState recovery) {
        for (var binding : recovery.current().values()) {
            if (binding.asset() != FencedRecoveryAsset.BODY) continue;
            requireDeclared(actors, binding.bindingId(), binding.ownerId(), binding.ownerRevision());
            if (binding.phase() == FencedRecoveryPhase.CONFIRMED)
                throw new IllegalArgumentException("confirmed body authority must be retired, not current");
        }
        for (var retired : recovery.tombstones().values()) {
            if (retired.asset() == FencedRecoveryAsset.BODY)
                requireDeclared(actors, retired.bindingId(), retired.ownerId(), retired.ownerRevision());
        }
    }
    private static void requireDeclared(
            java.util.Map<io.farfrontier.palemirror.frontier.v3.api.SubjectId, ActorLocation> actors,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId binding,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor, long revision) {
        if (!actors.containsKey(actor) || revision != 0L || !binding.equals(ActorBodyId.recoveryBindingId(actor)))
            throw new IllegalArgumentException("physical body requires its exact actor owner, not an activity/scene");
    }

    /** Representation demand never transfers an existing incarnation to its requester. */
    public static FrontierWorldState demand(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        requireLiving(state, actor);
        var recovery = demand(state.fencedRecovery(), actor);
        return recovery == state.fencedRecovery() ? state : state.withChanges(
                FrontierWorldStateUpdate.begin().fencedRecovery(recovery));
    }
    static FencedRecoveryState demand(FencedRecoveryState recovery,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        Objects.requireNonNull(actor, "declared body actor");
        var id = ActorBodyId.recoveryBindingId(actor);
        var retained = recovery.current().get(id);
        if (retained != null) {
            require(recovery, new ActorBodyId(actor, retained.authorityEpoch()));
            return recovery;
        }
        return recovery.prepare(FencedRecoveryBinding.prepared(id, FencedRecoveryAsset.BODY,
                actor, 0L, recovery.nextEpoch(id), true));
    }
    /** The identity comes from this owner's retained record, never from a scene revision. */
    public static ActorBodyId current(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var binding = state.fencedRecovery().current().get(ActorBodyId.recoveryBindingId(actor));
        if (binding == null) throw new IllegalArgumentException("actor has no retained physical incarnation");
        var body = new ActorBodyId(actor, binding.authorityEpoch());
        require(state, body);
        return body;
    }

    public static ActorBodyId next(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        requireLiving(state, actor);
        return new ActorBodyId(actor, state.fencedRecovery().nextEpoch(ActorBodyId.recoveryBindingId(actor)));
    }
    /** Read-only proposal for representation demand; physical evidence never allocates an epoch. */
    public static ActorBodyId demandIdentity(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        requireLiving(state, actor);
        return state.fencedRecovery().current().containsKey(ActorBodyId.recoveryBindingId(actor))
                ? current(state, actor) : next(state, actor);
    }
    public static FrontierWorldState prepare(FrontierWorldState state, ActorBodyId body) {
        requireLiving(state, body.actorId());
        var id = ActorBodyId.recoveryBindingId(body.actorId());
        if (state.fencedRecovery().current().containsKey(id)
                || body.physicalEpoch() != state.fencedRecovery().nextEpoch(id))
            throw new IllegalArgumentException("body preparation requires exact vacant physical authority");
        var binding = FencedRecoveryBinding.prepared(id, FencedRecoveryAsset.BODY,
                body.actorId(), 0L, body.physicalEpoch(), true);
        return state.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(state.fencedRecovery().prepare(binding)));
    }
    public static FencedRecoveryBinding require(FrontierWorldState state, ActorBodyId body) {
        Objects.requireNonNull(state, "body authority state"); Objects.requireNonNull(body, "body identity");
        return require(state.fencedRecovery(), body);
    }
    /** Death retires actuation, not the independently retained resource evidence of that exact incarnation. */
    public static void requireRetiredDeath(FrontierWorldState state, ActorBodyId body) {
        var id = ActorBodyId.recoveryBindingId(body.actorId());
        var retired = state.fencedRecovery().tombstones().get(id);
        if (state.actorLocations().get(body.actorId()).condition().status() != ActorLifeStatus.DEAD
                || state.fencedRecovery().current().containsKey(id) || retired == null
                || retired.asset() != FencedRecoveryAsset.BODY || !retired.ownerId().equals(body.actorId())
                || retired.ownerRevision() != 0 || retired.retiredEpoch() != body.physicalEpoch()
                || retired.disposition() != FencedRecoveryDisposition.REJECT_STALE)
            throw new IllegalArgumentException("resource death evidence lacks its exact retired body identity");
    }
    private static FencedRecoveryBinding require(FencedRecoveryState recovery, ActorBodyId body) {
        var binding = recovery.current().get(ActorBodyId.recoveryBindingId(body.actorId()));
        if (binding == null || binding.asset() != FencedRecoveryAsset.BODY
                || !binding.ownerId().equals(body.actorId()) || binding.ownerRevision() != 0L
                || binding.authorityEpoch() != body.physicalEpoch())
            throw new IllegalArgumentException("body authority is absent, foreign or stale");
        return binding;
    }
    /** Only an exact observed body-death boundary retires this identity, not job completion. */
    static FencedRecoveryState death(FencedRecoveryState recovery, ActorBodyId body) {
        var binding = require(recovery, body);
        return recovery.retireObservedBodyDeath(binding.bindingId(), body.physicalEpoch(), body.actorId(), 0L);
    }
    /** One independent physical boundary, with family-owned consequences supplied as a port. */
    public static FrontierWorldState died(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyDied observed,
            ActorDeathConsequences consequences, long atTick) {
        requireLiving(state, observed.body().actorId());
        var actor = state.actorLocations().get(observed.body().actorId());
        var binding = require(state, observed.body());
        if (binding.phase() != FencedRecoveryPhase.RUNNING && binding.phase() != FencedRecoveryPhase.AMBIGUOUS)
            throw new IllegalArgumentException("body death requires an actually admitted incarnation");
        if (!actor.body().equals(observed.expectedBody()) || !actor.condition().health().equals(observed.expectedHealth()))
            throw new IllegalArgumentException("body death has a stale canonical baseline");
        var execution = state.actorExecutions().actors().get(observed.body().actorId());
        var current = execution == null ? java.util.Optional.<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId>empty()
                : execution.current();
        if (!current.equals(observed.expectedExecution()))
            throw new IllegalArgumentException("body death has stale execution evidence");
        var position = observed.observedBody().orElse(actor.body());
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), position.supportingSurface().support());
        var settlement = Objects.requireNonNull(consequences).settle(state, observed.body().actorId(), atTick);
        if (settlement.expectedState() != state
                || settlement.changes().changedComponents().contains(FrontierWorldStateUpdate.Component.ACTOR_LOCATIONS)
                || settlement.changes().changedComponents().contains(FrontierWorldStateUpdate.Component.FENCED_RECOVERY)
                || settlement.changes().changedComponents().contains(FrontierWorldStateUpdate.Component.ACTOR_EXECUTIONS))
            throw new IllegalArgumentException("death consequence port cannot replace physical/common execution authority");
        var actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(observed.body().actorId(), actor.deadAt(position));
        return ActorExecutionComposition.LIFECYCLE.preparePassiveDeath(state, observed.body().actorId(), settlement.executions())
                .commit(state, settlement.changes().actorLocations(actors).fencedRecovery(death(state.fencedRecovery(), observed.body())));
    }
    /** Physical observation and eligibility are independent from any family's reconciliation. */
    public static FrontierWorldState inspected(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected observed) {
        requireLiving(state, observed.body().actorId());
        var actor = state.actorLocations().get(observed.body().actorId());
        var execution = state.actorExecutions().actors().get(observed.body().actorId());
        var current = execution == null ? java.util.Optional.<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId>empty()
                : execution.current();
        if (!actor.body().equals(observed.expectedBody()) || !actor.condition().health().equals(observed.expectedHealth())
                || !current.equals(observed.expectedExecution()))
            throw new IllegalArgumentException("body inspection has stale canonical or execution evidence");
        var binding = require(state, observed.body());
        if (binding.phase() != FencedRecoveryPhase.RUNNING && binding.phase() != FencedRecoveryPhase.AMBIGUOUS)
            throw new IllegalArgumentException("body inspection cannot admit an uncreated incarnation");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), observed.observedBody().supportingSurface().support());
        var actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(observed.body().actorId(), new ActorLocation(observed.observedBody(),
                actor.condition().withHealth(observed.observedHealth()), actor.kind()));
        var recovery = binding.phase() == FencedRecoveryPhase.AMBIGUOUS
                && observed.source() == io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyInspected.Source.INDEXED_LIVING
                ? state.fencedRecovery().inspectedRunning(binding.bindingId(), observed.body().physicalEpoch())
                : state.fencedRecovery();
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).fencedRecovery(recovery));
    }
    public static FrontierWorldState running(FrontierWorldState state, ActorBodyId body) {
        requireLiving(state, body.actorId());
        var binding = require(state, body);
        return state.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                state.fencedRecovery().running(binding.bindingId(), body.physicalEpoch())));
    }
    public static void requireActuation(FrontierWorldState state, ActorActuationId actuation) {
        Objects.requireNonNull(actuation, "actuation authority");
        requireLiving(state, actuation.body().actorId());
        if (require(state, actuation.body()).phase() != FencedRecoveryPhase.RUNNING)
            throw new IllegalArgumentException("body actuator requires current running physical custody");
        state.actorExecutions().requireCurrent(actuation.execution());
    }
    /** An actual body is ready before a process group is ready; no semantic arrival is implied. */
    public static FrontierWorldState present(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyPresent observed) {
        requireLiving(state, observed.body().actorId());
        var actor = state.actorLocations().get(observed.body().actorId());
        var binding = require(state, observed.body());
        if (!actor.body().equals(observed.expectedBody()) || !actor.condition().health().equals(observed.expectedHealth()))
            throw new IllegalArgumentException("body presence has a stale canonical baseline");
        if (binding.phase() != FencedRecoveryPhase.PREPARED && binding.phase() != FencedRecoveryPhase.AMBIGUOUS)
            throw new IllegalArgumentException("body presence requires exact pending admission or inspection");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), observed.observedBody().supportingSurface().support());
        var actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(observed.body().actorId(), new ActorLocation(observed.observedBody(),
                actor.condition().withHealth(observed.observedHealth()), actor.kind()));
        var recovery = binding.phase() == FencedRecoveryPhase.PREPARED
                ? state.fencedRecovery().running(binding.bindingId(), observed.body().physicalEpoch())
                : state.fencedRecovery().inspectedRunning(binding.bindingId(), observed.body().physicalEpoch());
        return state.withChanges(FrontierWorldStateUpdate.begin().actorLocations(actors).fencedRecovery(recovery));
    }
    /** Includes unstarted/recovery custody: absence of observers never permits a competing writer. */
    public static boolean retainsPhysicalCustody(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        return retainsPhysicalCustody(state.fencedRecovery(), actor);
    }
    /** Aggregate validation uses the same owner before a complete state can be constructed. */
    static boolean retainsPhysicalCustody(FencedRecoveryState recovery,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var binding = recovery.current().get(ActorBodyId.recoveryBindingId(actor));
        if (binding == null) return false;
        require(recovery, new ActorBodyId(actor, binding.authorityEpoch()));
        return true;
    }
    /** The physical owner supplies absence only after its exact durable carrier fence. */
    public static FrontierWorldState released(FrontierWorldState state, ActorBodyId body) {
        requireLiving(state, body.actorId());
        if (ActorInventoryInteractionFences.pending(state, body.actorId()))
            throw new IllegalArgumentException("body release retains a prepared inventory interaction");
        var phase = require(state, body).phase();
        if (phase != FencedRecoveryPhase.RUNNING && phase != FencedRecoveryPhase.PREPARED)
            throw new IllegalArgumentException("body absence requires an exact unambiguous incarnation");
        var checkpoint = phase == FencedRecoveryPhase.RUNNING
                ? ActorExecutionComposition.LIFECYCLE.checkpointBodyDeparture(state, body,
                    state.actorLocations().get(body.actorId()).body()) : FrontierWorldStateUpdate.begin();
        return state.withChanges(checkpoint.inventory(state.inventory().withFungibleResources(
                UnitInventoryBodyCustody.departureResources(state, body))).fencedRecovery(
                state.fencedRecovery().revokeToCold(ActorBodyId.recoveryBindingId(body.actorId()), body.physicalEpoch())));
    }
    /** Physical facts do not complete, pause or replace an activity or a resource operation. */
    public static FrontierWorldState unloaded(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyUnloaded observed) {
        requireLiving(state, observed.body().actorId());
        var actor = state.actorLocations().get(observed.body().actorId());
        var execution = state.actorExecutions().actors().get(observed.body().actorId());
        var current = execution == null ? java.util.Optional.<io.farfrontier.palemirror.frontier.v3.model.execution.ActorExecutionId>empty()
                : execution.current();
        if (!actor.body().equals(observed.expectedBody()) || !actor.condition().health().equals(observed.expectedHealth())
                || !current.equals(observed.expectedExecution()))
            throw new IllegalArgumentException("body unload has a stale canonical baseline or execution");
        var binding = require(state, observed.body());
        if (binding.phase() != FencedRecoveryPhase.RUNNING && binding.phase() != FencedRecoveryPhase.AMBIGUOUS)
            throw new IllegalArgumentException("observed body unload requires an actually admitted incarnation");
        if (ActorInventoryInteractionFences.pending(state, observed.body().actorId()))
            throw new IllegalArgumentException("body unload retains a prepared inventory interaction");
        FrontierWorldStateSupport.requirePosition(state.bootstrap().bounds(), observed.observedBody().supportingSurface().support());
        var actors = new java.util.LinkedHashMap<>(state.actorLocations());
        actors.put(observed.body().actorId(), new ActorLocation(observed.observedBody(),
                actor.condition().withHealth(observed.observedHealth()), actor.kind()));
        // Exact saved-absence evidence may resolve body ambiguity, never a resource effect.
        // There is no published RUNNING intermediate or permission to actuate a saved body.
        var checkpoint = ActorExecutionComposition.LIFECYCLE.checkpointBodyDeparture(state, observed.body(), observed.observedBody());
        return state.withChanges(checkpoint.actorLocations(actors).inventory(state.inventory().withFungibleResources(
                UnitInventoryBodyCustody.departureResources(state, observed.body()))).fencedRecovery(
                state.fencedRecovery().retireObservedBodyAbsence(binding.bindingId(),
                    observed.body().physicalEpoch(), observed.body().actorId())));
    }
    public static FrontierWorldState isolate(FrontierWorldState state, ActorBodyId body, String reason) {
        var binding = require(state, body);
        return state.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(state.fencedRecovery().ambiguous(
                binding.bindingId(), body.physicalEpoch(), reason, FencedRecoveryDisposition.INSPECT)));
    }
    /** Exact inspection resumes this incarnation; it never allocates another body or activity. */
    public static FrontierWorldState inspectedRunning(FrontierWorldState state, ActorBodyId body) {
        requireLiving(state, body.actorId());
        var binding = require(state, body);
        return state.withChanges(FrontierWorldStateUpdate.begin().fencedRecovery(
                state.fencedRecovery().inspectedRunning(binding.bindingId(), body.physicalEpoch())));
    }
    private static void requireLiving(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var location = Objects.requireNonNull(state, "body authority state").actorLocations().get(Objects.requireNonNull(actor));
        if (location == null || location.condition().status() != ActorLifeStatus.ALIVE)
            throw new IllegalArgumentException("living body authority requires its exact living canonical actor");
    }
}
