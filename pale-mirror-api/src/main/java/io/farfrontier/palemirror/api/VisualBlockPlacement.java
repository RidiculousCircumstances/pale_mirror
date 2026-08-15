package io.farfrontier.palemirror.api;

import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;

/** One curated cell compiled from a Visuals-owned blueprint, including optional local block-entity state. */
public record VisualBlockPlacement(VisualPoint position, BlockState state,
                                   Optional<CompoundTag> blockEntityData) {
    public VisualBlockPlacement {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(blockEntityData, "blockEntityData");
        blockEntityData = blockEntityData.map(CompoundTag::copy);
        if (blockEntityData.isPresent() && !state.hasBlockEntity()) {
            throw new IllegalArgumentException("Block-entity data requires a block-entity state at " + position
                    + ": " + state);
        }
    }

    public VisualBlockPlacement(VisualPoint position, BlockState state) {
        this(position, state, Optional.empty());
    }

    public VisualBlockPlacement(VisualPoint position, BlockState state, CompoundTag blockEntityData) {
        this(position, state, Optional.of(Objects.requireNonNull(blockEntityData, "blockEntityData")));
    }

    /** CompoundTag is mutable; callers never receive the snapshot-owned instance. */
    @Override public Optional<CompoundTag> blockEntityData() {
        return blockEntityData.map(CompoundTag::copy);
    }
}
