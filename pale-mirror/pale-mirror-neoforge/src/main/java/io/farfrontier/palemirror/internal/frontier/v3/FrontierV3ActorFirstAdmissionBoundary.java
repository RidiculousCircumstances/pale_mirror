package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.function.BooleanSupplier;

/** One pre-effect boundary for the first ambient or scene body; requires a previously issued permit. */
final class FrontierV3ActorFirstAdmissionBoundary {
    private FrontierV3ActorFirstAdmissionBoundary() { }
    static boolean admit(FrontierV3AmbientCarrierLedger ledger, FrontierV3ActorOwnerBinding target,
                         Runnable persist, BooleanSupplier addFreshEntity) {
        if (!ledger.beginFirstAdmission(target)) return false;
        var pending = ledger.firstAdmission(target.declaration().actorId()).orElseThrow();
        persist.run();
        // Throwing/unknown outcome remains pending. A synchronous false restores
        // only a fresh permission: an older offline absence proof cannot be reused
        // after live-world activity, even when this particular call inserted nothing.
        if (addFreshEntity.getAsBoolean()) return true;
        if (!ledger.rejectUncreatedFirstAdmission(pending))
            throw new IllegalStateException("rejected first admission changed custody");
        persist.run();
        return false;
    }
}
