package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
public record ShipmentColdTransferred(SubjectId shipmentId, long expectedRevision, Shipment.Status expectedStatus) implements FrontierPayload {
    public ShipmentColdTransferred {
        Objects.requireNonNull(shipmentId); Objects.requireNonNull(expectedStatus);
        if (expectedRevision < 1 || expectedStatus == Shipment.Status.DELIVERED || expectedStatus == Shipment.Status.ALLOCATION_WITHDRAWN)
            throw new IllegalArgumentException("shipment transfer requires a live exact predecessor");
    }
    @Override public String type() { return "frontier.shipment_cold_transferred"; }
}
