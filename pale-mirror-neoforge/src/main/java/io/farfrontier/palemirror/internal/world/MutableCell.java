package io.farfrontier.palemirror.internal.world;

import net.minecraft.core.BlockPos;

/** Sparse ownership record. Unknown changes are conflicts, never safe overwrite targets. */
public final class MutableCell {
    private final BlockPos position;
    private final String baselineBlock;
    private String lastAppliedBlock;
    private boolean conflicted;

    public MutableCell(BlockPos position, String baselineBlock, String lastAppliedBlock, boolean conflicted) {
        this.position = position.immutable();
        this.baselineBlock = baselineBlock;
        this.lastAppliedBlock = lastAppliedBlock;
        this.conflicted = conflicted;
    }

    public BlockPos position() { return position; }
    public String baselineBlock() { return baselineBlock; }
    public String lastAppliedBlock() { return lastAppliedBlock; }
    public boolean conflicted() { return conflicted; }
    public void markApplied(String id) { lastAppliedBlock = id; }
    public void conflict() { conflicted = true; }
}
