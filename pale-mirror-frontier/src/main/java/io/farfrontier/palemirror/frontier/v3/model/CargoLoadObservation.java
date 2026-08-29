package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;

/** Exact receipt that one active-depot stack left its physical slot for one named cargo batch. */
public record CargoLoadObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId contractId,
                                   SubjectId cargoId, SubjectId itemId, int itemCount) implements PhysicalEffectObservation {
    public CargoLoadObservation {
        Objects.requireNonNull(id, "cargo-load observation id"); Objects.requireNonNull(intentId, "cargo-load intent id");
        Objects.requireNonNull(contractId, "cargo-load contract id"); Objects.requireNonNull(cargoId, "cargo-load cargo id");
        Objects.requireNonNull(itemId, "cargo-load item id");
        if (!contractId.value().startsWith("contract:") || !cargoId.value().startsWith("cargo:")
                || !itemId.value().startsWith("item:") || itemCount < 1 || itemCount > 64) {
            throw new IllegalArgumentException("cargo-load receipt is invalid");
        }
    }
}
