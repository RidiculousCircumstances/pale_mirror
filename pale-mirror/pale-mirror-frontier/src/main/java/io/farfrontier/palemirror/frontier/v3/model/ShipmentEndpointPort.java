package io.farfrontier.palemirror.frontier.v3.model;

import java.util.List;

/** Endpoint owns its declared identity/access; transport owns cargo, not facility interpretation. */
interface ShipmentEndpointPort {
    ShipmentEndpoint.Kind kind();
    ShipmentReceiverReplacement replacementPolicy();
    void validate(FrontierWorldState state, ShipmentEndpoint endpoint);
    /** Complete immutable inputs of validate, excluding its already declared endpoint argument. */
    default Object[] validationInputs(FrontierWorldState state, ShipmentEndpoint endpoint) {
        var reads = validationReads(endpoint); var inputs = new Object[reads.size() + 1];
        inputs[0] = state.bootstrap();
        for (int index = 0; index < reads.size(); index++) inputs[index + 1] = reads.get(index).value(state);
        return inputs;
    }
    /** Epoch/bootstrap is supplied by the enclosing validator; ports declare exact dynamic reads. */
    List<ImmutableReferenceIndex.Read<FrontierWorldState>> validationReads(ShipmentEndpoint endpoint);
    List<KnownPedestrianRouteKnowledge.SettlementPassage> passages(FrontierWorldState state, ShipmentEndpoint endpoint);
    SurfaceAnchor exteriorApproach(FrontierWorldState state, ShipmentEndpoint endpoint);
    /** Immutable declared service geometry; not stock, readiness or interaction permission. */
    List<SurfaceAnchor> declaredStations(FrontierBootstrap bootstrap, ShipmentEndpoint endpoint);
    List<SurfaceAnchor> stations(FrontierWorldState state, ShipmentEndpoint endpoint);
    /** Admission candidates only. The selector retains one exact endpoint; continuations never rediscover it. */
    default List<SurfaceAnchor> approachStations(FrontierWorldState state,ShipmentEndpoint endpoint,KnownPedestrianRouteKnowledge actorKnowledge) {
        return stations(state,endpoint);
    }
    SurfaceAnchor interactionStation(FrontierWorldState state, ShipmentEndpoint endpoint);
}
