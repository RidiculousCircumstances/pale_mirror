package io.farfrontier.palemirror.api;

import java.util.Objects;
import net.minecraft.world.level.block.state.BlockState;

/** One inert, block-entity-free cell compiled from a Visuals-owned blueprint. */
public record VisualBlockPlacement(VisualPoint position, BlockState state) {
    public VisualBlockPlacement {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(state, "state");
    }
}
