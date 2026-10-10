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

    /** Scope owners may abandon a request, but never undo admission history themselves. */
    static boolean cancelUnstartedInsertion(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var ticket = FrontierV3BodyInsertionJournal.get(level, state.bootstrap().worldId(), actor);
        if (ticket == null || !unstartedAbsenceProven(level, runtime, state, actor)) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        FrontierV3BodyInsertionJournal.reject(level, ledger, ticket);
        ledger.persist(level, state.bootstrap().worldId());
        return true;
    }

    /** An unattempted insertion may be cancelled; an empty lookup cannot establish that fact. */
    static boolean releaseUnstartedAbsence(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
                                          io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        var state = runtime.decodedState().orElseThrow();
        if (!unstartedAbsenceProven(level, runtime, state, actor)) return false;
        var body = ActorBodyAuthority.current(state, actor);
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var unstarted = FrontierV3BodyInsertionJournal.get(level, state.bootstrap().worldId(), actor);
        if (unstarted != null) FrontierV3BodyInsertionJournal.reject(level, ledger, unstarted);
        ledger.persist(level, state.bootstrap().worldId());
        return FrontierV3CommandSubmission.submit(runtime, "actor-body-unstarted-release", actor.value(), new ActorBodyReleased(body))
                instanceof io.farfrontier.palemirror.frontier.v3.api.CommandResult.Accepted;
    }

    /** Shared positive admission-history proof; no caller may derive absence from a UUID lookup. */
    static boolean unstartedAbsenceProven(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId actor) {
        if (io.farfrontier.palemirror.frontier.v3.model.ActorInventoryInteractionFences.pending(state, actor)) return false;
        var body = ActorBodyAuthority.current(state, actor);
        if (ActorBodyAuthority.require(state, body).phase() != FencedRecoveryPhase.PREPARED) return false;
        var id = ActorBodyId.entityId(state.bootstrap().worldId(), actor);
        if (level.getEntity(id) != null || FrontierV3AmbientPendingAdmissions.get(runtime, id) != null) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var unstarted = FrontierV3BodyInsertionJournal.get(level, state.bootstrap().worldId(), actor);
        if (unstarted != null) return unstarted.binding().declaration().epoch() == body.physicalEpoch()
                && unstarted.current(ledger) && !ledger.hasDepartureConflict(actor);
        return unstartedEvidence(body, state.actorLocations().get(actor).kind(), id, ledger.firstAdmission(actor),
                ledger.inactiveCarrier(actor), ledger.pendingAdoption(actor).isPresent()
                        || ledger.hasDepartureConflict(actor));
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

}
