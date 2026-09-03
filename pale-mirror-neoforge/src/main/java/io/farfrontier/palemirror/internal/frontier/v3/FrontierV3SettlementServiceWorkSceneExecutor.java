package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;

/**
 * Registered physical owner for the service-work scene family.
 *
 * <p>MAT-003 has not yet admitted service work or emitted a service lease, so this deliberately
 * has no candidate or effect fallback. The subsequent vertical adds its retained-worker HOT
 * executor here; it must not borrow production or medical scene behavior.</p>
 */
final class FrontierV3SettlementServiceWorkSceneExecutor {
    private FrontierV3SettlementServiceWorkSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return false;
    }
}
