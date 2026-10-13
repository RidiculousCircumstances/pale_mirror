package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** The receiving owner declares substitution; transport preserves the allocation and safe checkpoint. */
interface ShipmentReceiverReplacement {
    boolean retainedAvailable(FrontierWorldState state, Shipment shipment);
    List<ShipmentEndpoint> candidates(FrontierWorldState state, Shipment shipment, SurfaceAnchor origin);
    boolean admits(FrontierWorldState state, Shipment shipment, ShipmentEndpoint replacement);
}
