package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;

/**
 * Registered physical owner for the service-work scene family.
 *
 * <p>MAT-003 now admits retained service work, but has not yet emitted a service lease. This
 * deliberately has no candidate or effect fallback: the subsequent vertical adds its
 * retained-worker HOT executor here, and must not borrow production or medical scene behavior.</p>
 */
final class FrontierV3SettlementServiceWorkSceneExecutor {
    private FrontierV3SettlementServiceWorkSceneExecutor() { }

    static boolean tick(ServerLevel level, FrontierV3ServerRuntime<FrontierWorldState, ?> runtime) {
        return false;
    }
}
