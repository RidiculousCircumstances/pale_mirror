package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import java.util.Objects;

/** Before physical startup, reconcile write-ahead permissions against fully recovered canonical state. */
final class FrontierV3ActorBirthRecovery {
    private FrontierV3ActorBirthRecovery() { }
    static int retireUnpublished(FrontierWorldState recovered, FrontierV3AmbientCarrierLedger ledger, Runnable persist) {
        Objects.requireNonNull(recovered); Objects.requireNonNull(ledger); Objects.requireNonNull(persist);
        // Bounded permission journal only, not a discovery scan or a second actor roster.
        var candidates = ledger.firstAdmissions().stream()
                .filter(permission -> permission.phase() == FrontierV3ActorFirstAdmission.Phase.NEVER_CREATED
                        && !recovered.actorLocations().containsKey(permission.identity().actorId()))
                .toList();
        if (!ledger.retireUnbornPermissions(recovered, candidates))
            throw new IllegalStateException("unpublished birth contradicts retained custody");
        int retired = candidates.size();
        if (retired != 0) persist.run();
        return retired;
    }
}
