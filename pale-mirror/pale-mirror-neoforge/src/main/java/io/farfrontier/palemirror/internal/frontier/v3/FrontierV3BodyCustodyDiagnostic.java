package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorBodyId;
import net.minecraft.server.level.ServerLevel;

/** One actor's retained body evidence. Read-only, with no entity scan, load or recovery request. */
record FrontierV3BodyCustodyDiagnostic(String phase, long epoch, String firstAdmission,
        long carrierEpoch, boolean departure, boolean departureCurrent, boolean departureSaved,
        boolean currentResidence, boolean departureConflict, boolean pendingAdoption, String storedRecovery,
        long entityWriteEpoch) {
    static FrontierV3BodyCustodyDiagnostic read(ServerLevel level,
            FrontierV3ServerRuntime<FrontierWorldState, ?> runtime, FrontierWorldState state, SubjectId actor) {
        var binding = state.fencedRecovery().current().get(ActorBodyId.recoveryBindingId(actor));
        var ledger = FrontierV3AmbientCarrierLedger.get(level, state.bootstrap().worldId());
        var departure = ledger.bodyDeparture(actor);
        return new FrontierV3BodyCustodyDiagnostic(binding == null ? "NONE" : binding.phase().name(),
                binding == null ? 0L : binding.authorityEpoch(),
                ledger.firstAdmission(actor).map(value -> value.phase().name()).orElse("NONE"),
                ledger.inactiveCarrier(actor).map(value -> value.identity().epoch()).orElse(0L),
                ledger.hasBodyDeparture(actor), departure.filter(value -> value.current(state)).isPresent(),
                departure.filter(ledger::savedBodyDeparture).isPresent(),
                departure.filter(value -> ledger.currentBodyResidence(actor, value.residenceGeneration())).isPresent(),
                ledger.hasDepartureConflict(actor), ledger.pendingAdoption(actor).isPresent(),
                FrontierV3BodyStoredRecovery.diagnostic(runtime, actor),
                FrontierV3EntityWriteEpochs.globalStamp(level).orElse(-1L));
    }
}
