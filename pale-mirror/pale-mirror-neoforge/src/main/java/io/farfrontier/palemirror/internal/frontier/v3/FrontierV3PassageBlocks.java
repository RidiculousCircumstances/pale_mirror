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
    /** Vanilla gate use reverses its swing when opened from the opposite side.
     * Planning must witness that complete native transition before the packet runs. */
    static BlockState playerTransition(BlockState state, net.minecraft.core.Direction playerDirection) {
        boolean open = !state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN);
        if (state.getBlock() instanceof FenceGateBlock && open
                && state.getValue(FenceGateBlock.FACING) == playerDirection.getOpposite())
            state = state.setValue(FenceGateBlock.FACING, playerDirection);
        return state.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.OPEN, open);
    }
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
    /** A horizontal pair is a native passage relationship, independent of building ownership. */
    static Map<BlockPos, BlockState> linkedDoorStates(ServerLevel level, BlockPos position, boolean open) {
        var clicked=level.getBlockState(position);
        if (!(clicked.getBlock() instanceof DoorBlock) || opened(clicked).isEmpty()) return Map.of();
        var base=base(position,clicked);var result=new LinkedHashMap<>(states(level,base,open));
        if(result.isEmpty()) return Map.of();
        var facing=clicked.getValue(DoorBlock.FACING);var hinge=clicked.getValue(DoorBlock.HINGE);
        var other=base.relative(hinge==net.minecraft.world.level.block.state.properties.DoorHingeSide.RIGHT
                ? facing.getCounterClockWise() : facing.getClockWise());
        if (!FrontierV3LoadedWorld.ready(level,other)) return Map.copyOf(result);
        var candidate=level.getBlockState(other);
        if(candidate.is(clicked.getBlock()) && candidate.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER
                && candidate.getValue(DoorBlock.FACING)==facing && candidate.getValue(DoorBlock.HINGE)!=hinge)
            result.putAll(states(level,other,open));
        return Map.copyOf(result);
    }
    static void completeLinkedDoors(ServerLevel level, net.minecraft.world.entity.player.Player player,
            Map<BlockPos, BlockState> states) {
        // A native event may have replaced a leaf. Never let a linked follow-up
        // overwrite a new block or change a different hinge/facing/powered state.
        if(states.entrySet().stream().anyMatch(e -> {
            var actual=level.getBlockState(e.getKey());
            return !actual.is(e.getValue().getBlock())
                    || !actual.setValue(DoorBlock.OPEN,e.getValue().getValue(DoorBlock.OPEN)).equals(e.getValue());
        })) return;
        for(var entry:states.entrySet()) if(entry.getValue().getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER) {
            var actual=level.getBlockState(entry.getKey());
            if(actual.getBlock() instanceof DoorBlock door && !actual.equals(entry.getValue()))
                door.setOpen(player,level,actual,entry.getKey(),entry.getValue().getValue(DoorBlock.OPEN));
        }
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
