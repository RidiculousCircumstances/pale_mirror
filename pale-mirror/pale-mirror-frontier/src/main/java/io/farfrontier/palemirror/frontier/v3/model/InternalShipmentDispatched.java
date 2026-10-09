package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;

/** Allocation and courier admission are one transaction, never an orphan reservation. */
public record InternalShipmentDispatched(Shipment shipment, ClaimAllocation claim) implements FrontierPayload {
    public InternalShipmentDispatched { Objects.requireNonNull(shipment); Objects.requireNonNull(claim); }
    @Override public String type() { return "frontier.internal_shipment_dispatched"; }
}
