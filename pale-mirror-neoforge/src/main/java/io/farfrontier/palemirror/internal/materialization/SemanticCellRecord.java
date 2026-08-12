package io.farfrontier.palemirror.internal.materialization;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class SemanticCellRecord {
    private final BlockPos position;
    private final BlockState baselineState;
    private BlockState lastAppliedState;

    public SemanticCellRecord(BlockPos position, BlockState baselineState, BlockState lastAppliedState) {
        this.position = position.immutable();
        this.baselineState = java.util.Objects.requireNonNull(baselineState, "baselineState");
        this.lastAppliedState = java.util.Objects.requireNonNull(lastAppliedState, "lastAppliedState");
    }
    public BlockPos position() { return position; }
    public BlockState baselineState() { return baselineState; }
    public BlockState lastAppliedState() { return lastAppliedState; }
    public void applied(BlockState state) { lastAppliedState = java.util.Objects.requireNonNull(state, "state"); }
}
