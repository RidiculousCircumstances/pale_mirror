package io.farfrontier.palemirror.internal.frontier.v3;

import java.util.function.BooleanSupplier;

/** Durable-before-add ordering shared by ambient and scene reconstruction. */
final class FrontierV3ActorAdoptionAdmission {
    private FrontierV3ActorAdoptionAdmission() { }

    static boolean admit(FrontierV3AmbientCarrierLedger ledger,
                         FrontierV3ActorOwnerBinding binding,
                         Runnable persist, BooleanSupplier addFreshEntity) {
        if (!ledger.adopt(binding)) return false;
        var pending = ledger.pendingAdoption(binding.declaration().actorId()).orElseThrow();
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
