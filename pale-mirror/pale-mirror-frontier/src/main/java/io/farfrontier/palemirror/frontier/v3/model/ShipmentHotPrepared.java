package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
public record ShipmentHotPrepared(SubjectId shipmentId, ShipmentPhysicalStep step) implements FrontierPayload {
    public ShipmentHotPrepared { Objects.requireNonNull(shipmentId); Objects.requireNonNull(step); }
    @Override public String type() { return "frontier.shipment_hot_prepared"; }
}
