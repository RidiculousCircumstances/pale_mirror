package io.farfrontier.palemirror.internal.adapter;

import java.util.Objects;

import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;

/** A bounded, read-only observation of an already generated settlement. */
public record SettlementObservation(WorldObjectId settlementId, String dimensionId, BlockPos anchor,
                                    BlockPos minBounds, BlockPos maxBounds, int population,
                                    int guards, long observedAtGameTime, String provenance) {
    public SettlementObservation {
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        minBounds = Objects.requireNonNull(minBounds, "minBounds").immutable();
        maxBounds = Objects.requireNonNull(maxBounds, "maxBounds").immutable();
        if (population < 0 || guards < 0 || observedAtGameTime < 0) {
            throw new IllegalArgumentException("Settlement observation counts and time must not be negative");
        }
        provenance = Objects.requireNonNull(provenance, "provenance");
    }
}
