package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;

/** Read-only view of Vanilla's actual in-progress block action. */
public interface FrontierV3BlockBreakProgress {
    boolean frontierV3$isMining(BlockPos position);
}
