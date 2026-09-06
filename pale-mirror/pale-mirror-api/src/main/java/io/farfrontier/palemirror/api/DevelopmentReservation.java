package io.farfrontier.palemirror.api;

import java.util.Objects;

/** Future masterplan space only; it grants no runtime construction authority. */
public record DevelopmentReservation(String id, DevelopmentReservationKind kind,
                                     SettlementDevelopmentStage targetStage, VisualBounds bounds,
                                     String ownerBuildingId) {
    public DevelopmentReservation {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("reservation id is required");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(targetStage, "targetStage");
        Objects.requireNonNull(bounds, "bounds");
        ownerBuildingId = ownerBuildingId == null ? "" : ownerBuildingId;
        if (kind == DevelopmentReservationKind.ANNEX && ownerBuildingId.isBlank()) {
            throw new IllegalArgumentException("annex reservation requires an owner building");
        }
        if (kind == DevelopmentReservationKind.PARCEL && !ownerBuildingId.isBlank()) {
            throw new IllegalArgumentException("standalone parcel reservation cannot name an owner building");
        }
    }
}
