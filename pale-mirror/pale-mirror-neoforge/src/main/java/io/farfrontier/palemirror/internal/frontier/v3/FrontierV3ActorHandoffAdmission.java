package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.WorldId;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/** Callers validate canonical owner transition; this boundary retains its physical history before stamping. */
final class FrontierV3ActorHandoffAdmission {
    private FrontierV3ActorHandoffAdmission() { }
    static boolean transfer(ServerLevel level, WorldId world, Entity body,
                            FrontierV3ActorOwnerBinding target) {
        if (body.level() != level) return false;
        var ledger = FrontierV3AmbientCarrierLedger.get(level, world);
        return transfer(ledger, body, target, () -> ledger.persist(level, world));
    }

    /** Persistence seam; the metadata effect is fixed, not supplied by a caller. */
    static boolean transfer(FrontierV3AmbientCarrierLedger ledger, Entity body,
                            FrontierV3ActorOwnerBinding target, Runnable persist) {
        var from = FrontierV3ActorOwnerBinding.from(body).orElse(null);
        if (from == null) return false;
        if (!from.equals(target)) {
            if (!ledger.prepareHandoff(from, target)) return false;
            persist.run();
        }
        // No pose, health, inventory or physical generation changes here.
        target.stamp(body);
        return true;
    }
}
