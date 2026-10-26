package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.server.level.ServerLevel;
import java.util.Optional;

/** Read-only departure proof shared by dependent inventory owners. No effect or body mutation. */
final class FrontierV3DepartedBodyEvidence {
    private FrontierV3DepartedBodyEvidence() { }
    static Optional<ActorBodyId> retainedBody(FrontierWorldState state, SubjectId actor) {
        var id = ActorBodyId.recoveryBindingId(actor);
        if (state.fencedRecovery().current().containsKey(id)) return Optional.of(ActorBodyAuthority.current(state, actor));
        var retired = state.fencedRecovery().tombstones().get(id);
        if (retired == null) return Optional.empty();
        var body = new ActorBodyId(actor, retired.retiredEpoch());
        return ActorBodyAuthority.retainsRetiredDeparture(state, body) ? Optional.of(body) : Optional.empty();
    }
    static boolean absent(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime,
            FrontierWorldState state, ActorBodyId body) {
        var entity = ActorBodyId.entityId(state.bootstrap().worldId(), body.actorId());
        if (level.getEntity(entity) != null || FrontierV3AmbientPendingAdmissions.get(runtime, entity) != null) return false;
        // Durable RESUME_COLD is sufficient for cancelling an unused effect: no physical
        // inventory is inferred, and the common native journal must separately prove unused.
        if (ActorBodyAuthority.retainsRetiredDeparture(state, body)) return true;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var departure = ledger.bodyDeparture(body.actorId()).orElse(null);
        return departure != null && departure.identity().epoch() == body.physicalEpoch()
                && ledger.savedBodyDeparture(departure) && departure.retainedForDependentCheckpoint(state)
                && ledger.currentBodyResidence(body.actorId(), departure.residenceGeneration())
                && !FrontierV3ActorBodyController.departureReadPending(level, departure);
    }
}
