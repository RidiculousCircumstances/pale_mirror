package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;
public record ShipmentRetired(SubjectId shipmentId, long expectedRevision) implements FrontierPayload {
    public ShipmentRetired { Objects.requireNonNull(shipmentId); if (expectedRevision < 1) throw new IllegalArgumentException("invalid shipment revision"); }
    @Override public String type() { return "frontier.shipment_retired"; }
}
