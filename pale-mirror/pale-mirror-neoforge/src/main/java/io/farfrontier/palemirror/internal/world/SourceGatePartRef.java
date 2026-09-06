package io.farfrontier.palemirror.internal.world;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;

/** Persisted physical reference for one opaque part of a source-owned gate. */
public record SourceGatePartRef(String slotId, String profileId, BlockPos position, UUID entityId, Status status) {
    public enum Status { MISSING, ACTIVE, DEFEATED, REMOVED }

    public SourceGatePartRef {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(status, "status");
        if (slotId.isBlank() || profileId.isBlank()) throw new IllegalArgumentException("Gate part requires slot and profile");
    }
}
