package io.farfrontier.palemirror.internal.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

@FunctionalInterface
public interface RailPlacementAuthority {
    boolean mayReplace(ServerLevel level, String connectionId, BlockPos position,
                       BlockState currentState, BlockState proposedState);
}
