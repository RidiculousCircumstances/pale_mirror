package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;

/** Explicit physical contact provider; recognizing a pose grants no activity or body ownership. */
interface FrontierV3SeatCarrier {
    boolean contactEvidence(Entity passenger, BlockGetter geometry, BlockPos support);
}
