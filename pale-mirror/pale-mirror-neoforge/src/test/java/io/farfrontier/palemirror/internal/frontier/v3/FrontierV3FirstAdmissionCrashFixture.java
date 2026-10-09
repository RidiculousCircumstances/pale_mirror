package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.function.BooleanSupplier;

/** Test fixture for synchronous effect/crash injection; production uses its retained journal ticket. */
final class FrontierV3FirstAdmissionCrashFixture {
    private FrontierV3FirstAdmissionCrashFixture() { }
    static boolean admit(FrontierV3AmbientCarrierLedger ledger, FrontierV3ActorOwnerBinding target,
                         Runnable persist, BooleanSupplier addFreshEntity) {
        return admit(ledger, target, () -> { }, persist, addFreshEntity);
    }
    static boolean admit(FrontierV3AmbientCarrierLedger ledger, FrontierV3ActorOwnerBinding target,
                         Runnable prepare, Runnable persist, BooleanSupplier addFreshEntity) {
        if (!ledger.beginFirstAdmission(target)) return false;
        var pending = ledger.firstAdmission(target.declaration().actorId()).orElseThrow();
        prepare.run();
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
