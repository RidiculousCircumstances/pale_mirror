package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.List;
import java.util.Objects;

/** The transport workflow owns shipment references, not goods title, cargo totals or actor poses. */
public record TransportMission(SubjectId id, SubjectId groupId, List<SubjectId> shipmentIds,
                               ShipmentEndpoint sender, ShipmentEndpoint receiver,
                               SurfaceAnchor homeRendezvous, SurfaceAnchor destinationRendezvous, Stage stage, long revision) {
    public enum Stage { LOADING, OUTBOUND, UNLOADING, RETURNING, COMPLETE }
    public TransportMission {
        Objects.requireNonNull(id); Objects.requireNonNull(groupId); Objects.requireNonNull(sender);
        Objects.requireNonNull(receiver); Objects.requireNonNull(stage); shipmentIds = List.copyOf(shipmentIds);
        Objects.requireNonNull(homeRendezvous); Objects.requireNonNull(destinationRendezvous);
        if (shipmentIds.isEmpty() || shipmentIds.size() > 32 || shipmentIds.stream().distinct().count() != shipmentIds.size()
                || revision < 1 || sender.containerId().equals(receiver.containerId()))
            throw new IllegalArgumentException("transport mission requires exact shipments and distinct endpoints");
    }
    public TransportMission advance(Stage next) {
        boolean legal = switch (stage) {
            case LOADING -> next == Stage.OUTBOUND || next == Stage.RETURNING;
            case OUTBOUND -> next == Stage.UNLOADING || next == Stage.RETURNING;
            case UNLOADING -> next == Stage.RETURNING;
            case RETURNING -> next == Stage.COMPLETE;
            case COMPLETE -> false;
        };
        if (!legal) throw new IllegalArgumentException("illegal transport workflow transition");
        return new TransportMission(id, groupId, shipmentIds, sender, receiver, homeRendezvous, destinationRendezvous, next, revision + 1);
    }
}
