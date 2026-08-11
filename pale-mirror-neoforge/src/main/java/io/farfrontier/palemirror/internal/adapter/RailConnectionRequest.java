package io.farfrontier.palemirror.internal.adapter;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public record RailConnectionRequest(String connectionId, BlockPos start, BlockPos target,
                                    Direction.Axis trackAxis, int maximumLength,
                                    RailConstructionPolicy constructionPolicy) {
    public RailConnectionRequest(String connectionId, BlockPos start, BlockPos target,
                                 Direction.Axis trackAxis, int maximumLength) {
        this(connectionId, start, target, trackAxis, maximumLength,
                RailConstructionPolicy.LOADED_CHUNKS_ONLY);
    }

    public RailConnectionRequest {
        if (connectionId == null || connectionId.isBlank() || start == null || target == null
                || trackAxis == null || maximumLength < 1 || constructionPolicy == null) {
            throw new IllegalArgumentException("Invalid rail connection request");
        }
        start = start.immutable();
        target = target.immutable();
    }
}
