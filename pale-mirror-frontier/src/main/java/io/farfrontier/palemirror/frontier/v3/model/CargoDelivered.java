package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.List;
import java.util.Objects;

/** Canonical COLD receipt for an exact shipment whose destination has no active physical surface. */
public record CargoDelivered(SubjectId operationId, SubjectId cargoId, List<CargoHandoffPlacement> placements) implements FrontierPayload {
    public CargoDelivered {
        Objects.requireNonNull(operationId, "operation id"); Objects.requireNonNull(cargoId, "cargo id");
        placements = List.copyOf(placements);
        if (placements.isEmpty() || placements.stream().map(CargoHandoffPlacement::itemId).distinct().count() != placements.size()
                || placements.stream().map(CargoHandoffPlacement::receiverSlot).distinct().count() != placements.size()) {
            throw new IllegalArgumentException("cold cargo delivery requires distinct exact placements");
        }
    }
    @Override public String type() { return "frontier.cargo_delivered"; }
}
