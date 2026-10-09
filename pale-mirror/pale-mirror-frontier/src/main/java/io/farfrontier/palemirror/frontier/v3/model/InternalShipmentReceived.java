package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
public record InternalShipmentReceived(SubjectId shipmentId, long expectedRevision, SubjectId receiptId) implements FrontierPayload {
    public InternalShipmentReceived { Objects.requireNonNull(shipmentId); Objects.requireNonNull(receiptId);
        if (expectedRevision < 1) throw new IllegalArgumentException("invalid internal receipt revision"); }
    @Override public String type() { return "frontier.internal_shipment_received"; }
}
