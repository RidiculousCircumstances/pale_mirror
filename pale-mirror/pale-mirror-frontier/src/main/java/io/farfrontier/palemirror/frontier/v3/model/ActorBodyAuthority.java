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
    private static FencedRecoveryBinding require(FencedRecoveryState recovery, ActorBodyId body) {
        var binding = recovery.current().get(ActorBodyId.recoveryBindingId(body.actorId()));
        if (binding == null || binding.asset() != FencedRecoveryAsset.BODY
                || !binding.ownerId().equals(body.actorId()) || binding.ownerRevision() != 0L
                || binding.authorityEpoch() != body.physicalEpoch())
            throw new IllegalArgumentException("body authority is absent, foreign or stale");
        return binding;
    }
    static FencedRecoveryState observedPresent(FencedRecoveryState recovery,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var binding = recovery.current().get(ActorBodyId.recoveryBindingId(actor));
        if (binding == null) throw new IllegalArgumentException("body confirmation has no prepared incarnation");
        require(recovery, new ActorBodyId(actor, binding.authorityEpoch()));
        if (binding.phase() == FencedRecoveryPhase.RUNNING) return recovery;
        if (binding.phase() != FencedRecoveryPhase.PREPARED)
            throw new IllegalArgumentException("ambiguous body requires exact common-body inspection");
        return recovery.running(binding.bindingId(), binding.authorityEpoch());
    }
    public static FrontierWorldState observedPresent(FrontierWorldState state,
            io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        requireLiving(state, actor);
        var recovery = observedPresent(state.fencedRecovery(), actor);
        return recovery == state.fencedRecovery() ? state : state.withChanges(
                FrontierWorldStateUpdate.begin().fencedRecovery(recovery));
    }
    /** Only an exact observed body-death boundary retires this identity, not job completion. */
    static FencedRecoveryState death(FencedRecoveryState recovery, ActorBodyId body) {
        var binding = require(recovery, body);
        return recovery.retireObservedBodyDeath(binding.bindingId(), body.physicalEpoch(), body.actorId(), 0L);
    }
    public static FencedRecoveryState observedDeath(FrontierWorldState state, ActorBodyId body) {
        requireLiving(state, body.actorId());
        return death(state.fencedRecovery(), body);
    }
    /** An exact inspection can confirm an unchanged running incarnation or resolve ambiguity. */
    public static FencedRecoveryState inspectedPresent(FrontierWorldState state, ActorBodyId body) {
        requireLiving(state, body.actorId());
        var binding = require(state, body);
        if (binding.phase() == FencedRecoveryPhase.RUNNING) return state.fencedRecovery();
        if (binding.phase() != FencedRecoveryPhase.AMBIGUOUS)
            throw new IllegalArgumentException("body inspection is not a confirmation of unstarted insertion");
        return state.fencedRecovery().inspectedRunning(binding.bindingId(), body.physicalEpoch());
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
