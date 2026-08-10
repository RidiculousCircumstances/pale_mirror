package io.farfrontier.palemirror.internal.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record RailConnectionRequest(String connectionId, BlockPos start, BlockPos target,
                                    Direction.Axis trackAxis, int maximumLength) {
    public RailConnectionRequest {
        if (connectionId == null || connectionId.isBlank() || start == null || target == null
                || trackAxis == null || maximumLength < 1) throw new IllegalArgumentException("Invalid rail connection request");
        start = start.immutable();
        target = target.immutable();
    }
}
