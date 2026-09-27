package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;

import java.util.Objects;

/** Durable job-owned, single-use physical effect boundary; -1 means no destination chest slot. */
public record BakeryPhysicalStep(BakeryWorkState.Phase phase, SceneLeaseId leaseId, int destinationSlot) {
    public BakeryPhysicalStep {
        Objects.requireNonNull(phase, "bakery physical phase");
        Objects.requireNonNull(leaseId, "bakery physical lease");
        if (destinationSlot < -1 || destinationSlot > 26)
            throw new IllegalArgumentException("bakery physical destination slot is outside its bounded chest");
        boolean needsSlot = phase == BakeryWorkState.Phase.STATION_LOAD
                || phase == BakeryWorkState.Phase.PROCESSING || phase == BakeryWorkState.Phase.DEPOT_DELIVERY;
        if (phase == BakeryWorkState.Phase.DELIVERED)
            throw new IllegalArgumentException("delivered bakery work has no physical effect");
        if (needsSlot == (destinationSlot < 0))
            throw new IllegalArgumentException("bakery physical effect has no exact destination slot");
    }
}
