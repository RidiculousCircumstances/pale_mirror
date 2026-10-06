package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.*;
import java.util.Objects;
public record ShipmentReceiptAcknowledged(SubjectId shipmentId, long expectedRevision, SubjectId receiptId) implements FrontierPayload {
    public ShipmentReceiptAcknowledged {
        Objects.requireNonNull(shipmentId); Objects.requireNonNull(receiptId);
        if (expectedRevision < 1) throw new IllegalArgumentException("invalid reception acknowledgement revision");
    }
    @Override public String type() { return "frontier.shipment_receipt_acknowledged"; }
}
