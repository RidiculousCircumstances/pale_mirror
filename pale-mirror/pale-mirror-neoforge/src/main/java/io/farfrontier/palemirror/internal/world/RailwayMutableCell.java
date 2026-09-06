package io.farfrontier.palemirror.internal.world;

import net.minecraft.core.BlockPos;

public final class RailwayMutableCell {
    private final BlockPos position;
    private final String baselineState;
    private String lastApprovedState;
    private boolean conflicted;

    public RailwayMutableCell(BlockPos position, String baselineState, String lastApprovedState, boolean conflicted) {
        this.position = position.immutable();
        this.baselineState = baselineState;
        this.lastApprovedState = lastApprovedState;
        this.conflicted = conflicted;
    }

    public BlockPos position() { return position; }
    public String baselineState() { return baselineState; }
    public String lastApprovedState() { return lastApprovedState; }
    public boolean conflicted() { return conflicted; }
    public void approve(String state) { lastApprovedState = state; }
    public void approveFirstGeneration(String state) {
        lastApprovedState = state;
        conflicted = false;
    }
    public void conflict() { conflicted = true; }
}
