package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;

/** Ambient protocol's read-only view of common body departure evidence. */
final class FrontierV3AmbientDepartureObserver {
    private FrontierV3AmbientDepartureObserver() { }

    /** Family projection receives an explicit actor; it never owns physical capture. */
    static java.util.Optional<FrontierV3AmbientDeparture> recordedDeparture(FrontierWorldState state, SubjectId actor,
                                                                           FrontierV3AmbientCarrierLedger ledger) {
        var physical = ledger.bodyDeparture(actor).orElse(null);
        if (physical == null) return ledger.ambientDeparture(actor).filter(value -> value.current(state));
        var lease = state.ambientLeases().get(actor);
        if (lease == null || !physical.current(state) || ledger.hasDepartureConflict(actor)) return java.util.Optional.empty();
        var receipt = new FrontierV3AmbientDeparture(new FrontierV3AmbientCarrierLedger.Carrier(physical.identity(),
                lease.revision(), lease.revision()), physical.residenceGeneration(), physical.observed(), physical.canonicalBody(), physical.canonicalHealth());
        return receipt.current(state) ? java.util.Optional.of(receipt) : java.util.Optional.empty();
    }

}
