package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SceneLeaseId;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Durable boundary where a live route cargo stops being an atomic shipment and becomes an
 * observed physical carrier. The later item-by-item player/drop/hopper observations retain the
 * same exact stack IDs without inventing a second transport inventory.
 */
public record CargoCarrierReleased(SceneLeaseId leaseId, SubjectId cargoId, UUID carrierId, Optional<UUID> observerPlayerId) implements FrontierPayload {
    public CargoCarrierReleased {
        Objects.requireNonNull(leaseId, "scene lease id");
        Objects.requireNonNull(cargoId, "cargo id");
        Objects.requireNonNull(carrierId, "carrier id");
        observerPlayerId = Objects.requireNonNull(observerPlayerId, "observer player id");
    }

    @Override public String type() { return "frontier.cargo_carrier_released"; }
    @Override public boolean requiresDurableBeforeEffect() { return true; }
}
