package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Endpoint owns its declared identity/access; transport owns cargo, not facility interpretation. */
interface ShipmentEndpointPort {
    ShipmentEndpoint.Kind kind();
    void validate(FrontierWorldState state, ShipmentEndpoint endpoint);
    List<KnownPedestrianRouteKnowledge.SettlementPassage> passages(FrontierWorldState state, ShipmentEndpoint endpoint);
    SurfaceAnchor exteriorApproach(FrontierWorldState state, ShipmentEndpoint endpoint);
}
