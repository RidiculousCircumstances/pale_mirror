package io.farfrontier.palemirror.internal.adapter;

import java.util.Objects;
import java.util.List;

import io.farfrontier.palemirror.domain.WorldObjectId;
import net.minecraft.core.BlockPos;

/** A bounded, read-only observation of an already generated settlement. */
public record SettlementObservation(WorldObjectId settlementId, String dimensionId, BlockPos anchor,
                                    BlockPos minBounds, BlockPos maxBounds, int population,
                                    int guards, long observedAtGameTime, boolean fullBoundsLoaded,
                                    List<SettlementRepresentativeObservation> representatives, String provenance,
                                    String authorityProfileId, String nativeReference) {
    public SettlementObservation(WorldObjectId settlementId, String dimensionId, BlockPos anchor,
                                 BlockPos minBounds, BlockPos maxBounds, int population,
                                 int guards, long observedAtGameTime, boolean fullBoundsLoaded,
                                 List<SettlementRepresentativeObservation> representatives, String provenance) {
        this(settlementId, dimensionId, anchor, minBounds, maxBounds, population, guards,
                observedAtGameTime, fullBoundsLoaded, representatives, provenance,
                "pale_mirror:pm_managed", "");
    }
    public SettlementObservation {
        Objects.requireNonNull(settlementId, "settlementId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        anchor = Objects.requireNonNull(anchor, "anchor").immutable();
        minBounds = Objects.requireNonNull(minBounds, "minBounds").immutable();
        maxBounds = Objects.requireNonNull(maxBounds, "maxBounds").immutable();
        if (population < 0 || guards < 0 || observedAtGameTime < 0) {
            throw new IllegalArgumentException("Settlement observation counts and time must not be negative");
        }
        representatives = List.copyOf(representatives);
        provenance = Objects.requireNonNull(provenance, "provenance");
        if (authorityProfileId == null || authorityProfileId.isBlank()) throw new IllegalArgumentException("authorityProfileId must not be blank");
        nativeReference = Objects.requireNonNull(nativeReference, "nativeReference");
    }

    public String observationId() {
        return "settlement:" + settlementId.value() + ":" + observedAtGameTime;
    }
}
