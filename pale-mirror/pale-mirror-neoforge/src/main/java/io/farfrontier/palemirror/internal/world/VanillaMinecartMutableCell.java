package io.farfrontier.palemirror.internal.world;

import net.minecraft.core.BlockPos;

/** Per-cell provenance for an authored vanilla freight corridor. */
public final class VanillaMinecartMutableCell {
    private final BlockPos position;
    private final String baselineState;
    private String lastAppliedState;
    private boolean conflicted;

    public VanillaMinecartMutableCell(BlockPos position, String baselineState, String lastAppliedState, boolean conflicted) {
        this.position = position.immutable();
        this.baselineState = baselineState;
        this.lastAppliedState = lastAppliedState;
        this.conflicted = conflicted;
    }

    public BlockPos position() { return position; }
    public String baselineState() { return baselineState; }
    public String lastAppliedState() { return lastAppliedState; }
    public boolean conflicted() { return conflicted; }
    public boolean accepts(String current) { return !conflicted && (baselineState.equals(current) || lastAppliedState.equals(current)); }
    public void apply(String state) { lastAppliedState = state; }
    public void conflict() { conflicted = true; }
    public void clearConflict() { conflicted = false; }
}
