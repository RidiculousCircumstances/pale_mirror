package io.farfrontier.palemirror.internal.world;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;

/** Persisted identity for one physical gate; entity UUID is absent for a block-backed Node. */
public record SiegePartRef(String slotId, SiegePartKind kind, String profileId, BlockPos position, UUID entityId,
                           Status status) {
    public enum Status { MISSING, ACTIVE, DEFEATED, REMOVED }

    public SiegePartRef {
        Objects.requireNonNull(slotId, "slotId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(profileId, "profileId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(status, "status");
    }
}
