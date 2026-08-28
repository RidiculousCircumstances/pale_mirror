package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.PhysicalIntentId;
import io.farfrontier.palemirror.frontier.v3.api.PhysicalObservationId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Immutable actual placement evidence produced by a real cargo hand-off. */
public record CargoHandoffObservation(PhysicalObservationId id, PhysicalIntentId intentId, SubjectId cargoId,
                                      List<CargoHandoffPlacement> placements) implements PhysicalEffectObservation {
    public CargoHandoffObservation {
        Objects.requireNonNull(id, "physical observation id");
        Objects.requireNonNull(intentId, "physical observation intent id");
        Objects.requireNonNull(cargoId, "physical observation cargo id");
        placements = List.copyOf(placements);
        if (placements.isEmpty() || placements.size() > 32
                || placements.stream().map(CargoHandoffPlacement::itemId).distinct().count() != placements.size()
                || placements.stream().map(CargoHandoffPlacement::receiverSlot).distinct().count() != placements.size()) {
            throw new IllegalArgumentException("cargo hand-off observation must contain distinct bounded item placements");
        }
    }
}
