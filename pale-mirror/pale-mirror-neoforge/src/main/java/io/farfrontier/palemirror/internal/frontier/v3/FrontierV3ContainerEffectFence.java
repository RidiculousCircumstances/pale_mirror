package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.ContainerPhysicalAuthorityComposition;
import io.farfrontier.palemirror.frontier.v3.model.FrontierWorldState;
import net.minecraft.server.level.ServerLevel;

/** Composes canonical owner fences with the field adapter's saved non-replayable delivery. */
final class FrontierV3ContainerEffectFence {
    private FrontierV3ContainerEffectFence() { }
    static boolean pending(ServerLevel level, FrontierWorldState state, SubjectId container) {
        return ContainerPhysicalAuthorityComposition.pending(state, container)
                || FrontierV3ResourceSiteLedger.get(level).hasPendingFieldDelivery(container);
    }
}
