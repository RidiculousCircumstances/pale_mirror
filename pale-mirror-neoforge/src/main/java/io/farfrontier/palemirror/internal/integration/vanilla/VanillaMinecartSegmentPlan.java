package io.farfrontier.palemirror.internal.integration.vanilla;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** One bounded, loaded-chunk write set for a PM-owned vanilla rail corridor. */
public record VanillaMinecartSegmentPlan(BlockPos railPosition, Map<BlockPos, BlockState> writes) {
    public VanillaMinecartSegmentPlan {
        railPosition = Objects.requireNonNull(railPosition, "railPosition").immutable();
        Objects.requireNonNull(writes, "writes");
        Map<BlockPos, BlockState> copy = new LinkedHashMap<>();
        writes.forEach((position, state) -> copy.put(Objects.requireNonNull(position, "position").immutable(),
                Objects.requireNonNull(state, "state")));
        if (copy.isEmpty()) throw new IllegalArgumentException("Minecart segment has no writes");
        writes = java.util.Collections.unmodifiableMap(copy);
    }
}
