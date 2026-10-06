package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import java.util.Objects;
public record ShipmentDispatched(Shipment shipment) implements FrontierPayload {
    public ShipmentDispatched { Objects.requireNonNull(shipment); }
    @Override public String type() { return "frontier.shipment_dispatched"; }
}
