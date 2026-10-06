package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.execution.ActorActuationId;
import java.util.Objects;

/** Exact carried-stack boundary, not a transfer, pose update or commercial receipt. */
public record ShipmentHandCustodyObserved(SubjectId shipmentId, ActorActuationId identity,
        Boundary boundary, FungiblePhysicalObservation.Stack hand) implements FrontierPayload {
    public enum Boundary { MATERIALIZED, SAVED_DEPARTURE }
    public ShipmentHandCustodyObserved {
        Objects.requireNonNull(shipmentId); Objects.requireNonNull(identity);
        Objects.requireNonNull(boundary); Objects.requireNonNull(hand);
        if (!identity.execution().activityOwnerId().equals(shipmentId))
            throw new IllegalArgumentException("shipment hand has a foreign execution owner");
    }
    @Override public String type() { return "frontier.shipment_hand_custody_observed"; }
}
