package io.farfrontier.palemirror.internal.frontier.v3;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.gameevent.GameEvent;
import java.util.*;

/** Native manual-passage capability. No item names, actor roles or hardcoded building coordinates. */
final class FrontierV3PassageBlocks {
    private FrontierV3PassageBlocks() { }
    static Optional<BlockState> opened(BlockState state) {
        if (state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand())
            return Optional.of(state.setValue(DoorBlock.OPEN, true));
        if (state.getBlock() instanceof FenceGateBlock) return Optional.of(state.setValue(FenceGateBlock.OPEN, true));
        return Optional.empty();
    }
    static Optional<BlockState> traversalState(BlockState state, BlockPos position, net.minecraft.world.level.BlockGetter blocks) {
        var opened = opened(state);
        if (opened.isEmpty() || opened.orElseThrow().equals(state)) return opened;
        if (state.getBlock() instanceof DoorBlock) {
            var partner = blocks.getBlockState(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? position.above() : position.below());
            if (!partner.is(state.getBlock()) || partner.getValue(DoorBlock.HALF) == state.getValue(DoorBlock.HALF)
                    || partner.getValue(DoorBlock.FACING) != state.getValue(DoorBlock.FACING)
                    || partner.getValue(DoorBlock.HINGE) != state.getValue(DoorBlock.HINGE)) return Optional.empty();
        }
        return opened(state);
    }
    static BlockPos base(BlockPos position, BlockState state) {
        return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                ? position.below() : position;
    }
    static double side(BlockPos base, BlockState state, net.minecraft.world.phys.Vec3 feet) {
        var direction = state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING);
        return direction.getAxis() == net.minecraft.core.Direction.Axis.X ? feet.x - base.getX() - .5 : feet.z - base.getZ() - .5;
    }
    static boolean isOpen(BlockState state) { return opened(state).filter(state::equals).isPresent(); }
    static Map<BlockPos, BlockState> states(ServerLevel level, BlockPos base, boolean open) {
        var state = level.getBlockState(base);
        if (opened(state).isEmpty()) return Map.of();
        if (state.getBlock() instanceof DoorBlock) {
            var upper = base.above();
            if (!FrontierV3LoadedWorld.ready(level, upper)) return Map.of();
            var top = level.getBlockState(upper);
            if (!top.is(state.getBlock()) || top.getValue(DoorBlock.HALF) != DoubleBlockHalf.UPPER
                    || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER) return Map.of();
            return Map.of(base, state.setValue(DoorBlock.OPEN, open), upper, top.setValue(DoorBlock.OPEN, open));
        }
        return Map.of(base, state.setValue(FenceGateBlock.OPEN, open));
    }
    static void set(ServerLevel level, Mob actor, BlockPos base, boolean open) {
        var state = level.getBlockState(base);
        if (state.getBlock() instanceof DoorBlock door) door.setOpen(actor, level, state, base, open);
        else if (state.getBlock() instanceof FenceGateBlock) {
            level.setBlock(base, state.setValue(FenceGateBlock.OPEN, open), 3);
            level.gameEvent(actor, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, base);
        }
    }
}
