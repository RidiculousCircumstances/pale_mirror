package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.function.BooleanSupplier;

/** Test fixture for synchronous effect/crash injection; production uses its retained journal ticket. */
final class FrontierV3AdoptionCrashFixture {
    private FrontierV3AdoptionCrashFixture() { }

    static boolean admit(FrontierV3AmbientCarrierLedger ledger,
                         FrontierV3ActorOwnerBinding binding,
                         Runnable persist, BooleanSupplier addFreshEntity) {
        return admit(ledger, binding, () -> { }, persist, addFreshEntity);
    }
    static boolean admit(FrontierV3AmbientCarrierLedger ledger,
                         FrontierV3ActorOwnerBinding binding, Runnable prepare,
                         Runnable persist, BooleanSupplier addFreshEntity) {
        if (!ledger.adopt(binding)) return false;
        var pending = ledger.pendingAdoption(binding.declaration().actorId()).orElseThrow();
        prepare.run();
        persist.run();
        // Exceptions intentionally retain the pending transfer. We cannot infer
        // that the effect did not happen from an interrupted/throwing provider.
        if (addFreshEntity.getAsBoolean()) return true;
        if (!ledger.rejectUncreatedAdoption(pending)) {
            throw new IllegalStateException("rejected actor admission changed custody");
        }
        persist.run();
        return false;
    }
}
