package io.farfrontier.palemirror.internal.world;

import net.minecraft.core.BlockPos;

/** Sparse ownership record. Unknown changes are conflicts, never safe overwrite targets. */
public final class MutableCell {
    /** Persisted marker used until the owning chunk first enters the ordinary server pipeline. */
    public static final String UNOBSERVED_BASELINE = "pale_mirror:unobserved";

    private final BlockPos position;
    private String baselineBlock;
    private final InfectionBiomeStage infectionStage;
    private String lastAppliedBlock;
    private boolean conflicted;

    public MutableCell(BlockPos position, String baselineBlock, String lastAppliedBlock, boolean conflicted) {
        this(position, baselineBlock, lastAppliedBlock, conflicted, InfectionBiomeStage.NODE);
    }

    public MutableCell(BlockPos position, String baselineBlock, String lastAppliedBlock, boolean conflicted,
                       InfectionBiomeStage infectionStage) {
        this.position = position.immutable();
        this.baselineBlock = baselineBlock;
        this.infectionStage = infectionStage == null ? InfectionBiomeStage.NODE : infectionStage;
        this.lastAppliedBlock = lastAppliedBlock;
        this.conflicted = conflicted;
    }

    public BlockPos position() { return position; }
    public String baselineBlock() { return baselineBlock; }
    public InfectionBiomeStage infectionStage() { return infectionStage; }
    public String lastAppliedBlock() { return lastAppliedBlock; }
    public boolean conflicted() { return conflicted; }
    public boolean baselineObserved() { return !UNOBSERVED_BASELINE.equals(baselineBlock); }
    public void observeBaseline(String id) {
        if (baselineObserved()) throw new IllegalStateException("Mutable cell baseline was already observed at " + position);
        baselineBlock = id;
        lastAppliedBlock = id;
    }
    public void markApplied(String id) { lastAppliedBlock = id; }
    public void conflict() { conflicted = true; }
}
