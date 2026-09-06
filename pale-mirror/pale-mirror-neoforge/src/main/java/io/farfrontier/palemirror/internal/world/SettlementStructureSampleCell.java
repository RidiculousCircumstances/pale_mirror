package io.farfrontier.palemirror.internal.world;

import net.minecraft.core.BlockPos;

public record SettlementStructureSampleCell(BlockPos position, String baselineBlock) {
    public SettlementStructureSampleCell {
        position = position.immutable();
        if (baselineBlock == null || baselineBlock.isBlank()) throw new IllegalArgumentException("Baseline block is required");
    }
}
