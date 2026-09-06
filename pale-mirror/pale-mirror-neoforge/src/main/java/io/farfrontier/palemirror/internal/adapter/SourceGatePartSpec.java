package io.farfrontier.palemirror.internal.adapter;

import java.util.Objects;

import net.minecraft.core.BlockPos;

/** Opaque physical descriptor emitted by a source adapter for its current gate phase. */
public record SourceGatePartSpec(String slotId, String profileId, BlockPos position) {
    public SourceGatePartSpec {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(position, "position");
        if (slotId.isBlank() || profileId.isBlank()) throw new IllegalArgumentException("Gate part spec requires slot and profile");
    }
}
