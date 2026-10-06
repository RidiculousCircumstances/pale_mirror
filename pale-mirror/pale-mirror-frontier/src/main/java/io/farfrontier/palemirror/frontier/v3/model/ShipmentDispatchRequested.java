package io.farfrontier.palemirror.frontier.v3.model;
import io.farfrontier.palemirror.frontier.v3.api.FrontierPayload;
import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** An authorizing economic owner submits a full declaration; admission never chooses missing roles. */
public record ShipmentDispatchRequested(SubjectId senderId, EconomicOwnerKind senderKind, Shipment shipment) implements FrontierPayload {
    public ShipmentDispatchRequested { Objects.requireNonNull(senderId); Objects.requireNonNull(senderKind); Objects.requireNonNull(shipment); }
    @Override public String type() { return "frontier.shipment_dispatch_requested"; }
}
