package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.ActorBodyAuthority;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyReleased;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import io.farfrontier.palemirror.frontier.v3.model.ActorKind;
import io.farfrontier.palemirror.frontier.v3.model.FencedRecoveryPhase;
import net.minecraft.server.level.ServerLevel;

/** One activity-independent boundary for proven physical absence; lookup alone is insufficient. */
final class FrontierV3ActorBodyCustody {
    private FrontierV3ActorBodyCustody() { }

    /** An unattempted insertion may be cancelled; an empty lookup cannot establish that fact. */
    static boolean releaseUnstartedAbsence(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                          io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var state = runtime.decodedState().orElseThrow();
        var body = ActorBodyAuthority.current(state, actor);
        if (ActorBodyAuthority.require(state, body).phase() != FencedRecoveryPhase.PREPARED) return false;
        var id = ActorBodyId.entityId(state.bootstrap().worldId(), actor);
        if (level.getEntity(id) != null || FrontierV3AmbientPendingAdmissions.get(runtime, id) != null) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!unstartedEvidence(body, state.actorLocations().get(actor).kind(), id, ledger.firstAdmission(actor),
                ledger.inactiveCarrier(actor), ledger.pendingAdoption(actor).isPresent()
                        || ledger.pendingHandoff(actor).isPresent() || ledger.hasDepartureConflict(actor))) return false;
        ledger.persist(level, state.bootstrap().worldId());
        return FrontierV3CommandSubmission.submit(runtime, "actor-body-unstarted-release", actor.value(), new ActorBodyReleased(body))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
    }
    static boolean unstartedEvidence(ActorBodyId body, ActorKind kind, java.util.UUID id,
                                    java.util.Optional<FrontierV3ActorFirstAdmission> first,
                                    java.util.Optional<FrontierV3AmbientCarrierLedger.Carrier> inactive, boolean pending) {
        if (pending) return false;
        boolean neverCreated = first.filter(value -> value.phase() == FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED
                && value.identity().actorId().equals(body.actorId()) && value.identity().kind() == kind
                && value.identity().entityId().equals(id)).isPresent();
        // A retained older fenced incarnation plus no pending insertion proves this PREPARED
        // successor was not attempted. Skipped unstarted epochs are allocated only canonically.
        boolean predecessor = inactive.filter(value -> value.identity().actorId().equals(body.actorId())
                && value.identity().kind() == kind && value.identity().entityId().equals(id)
                && value.identity().epoch() < body.physicalEpoch()).isPresent();
        return neverCreated || predecessor;
    }

    static void releaseFencedAbsence(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                    FrontierV3ActorCarrierComposition.Declaration inactive,
                                    long physicalRevision, long ambientRevision) {
        var state = runtime.decodedState().orElseThrow();
        var body = ActorBodyAuthority.current(state, inactive.actorId());
        var retainedEntity = level.getEntity(inactive.entityId());
        if (inactive.representation() != FrontierV3ActorCarrierComposition.Representation.INACTIVE_CARRIER
                || inactive.epoch() != body.physicalEpoch()
                || !inactive.entityId().equals(ActorBodyId.entityId(state.bootstrap().worldId(), body.actorId()))
                || inactive.kind() != state.actorLocations().get(inactive.actorId()).kind()
                || retainedEntity != null && !retainedEntity.isRemoved()
                || FrontierV3AmbientPendingAdmissions.get(runtime, inactive.entityId()) != null)
            throw new IllegalArgumentException("body release lacks exact physical absence");
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        if (!ledger.matchesCarrier(inactive, physicalRevision, ambientRevision))
            throw new IllegalArgumentException("body absence lacks its persisted inactive carrier fence");
        FrontierV3CommandSubmission.submit(runtime, "actor-body-release", inactive.actorId().value(),
                new ActorBodyReleased(body));
    }
}
