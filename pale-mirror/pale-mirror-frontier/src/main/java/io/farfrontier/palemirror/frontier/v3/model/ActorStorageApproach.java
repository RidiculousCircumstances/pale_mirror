package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult;
import java.util.Optional;

/** Read-only service-station selection using the actual actor envelope and the facility's declared candidates. */
public final class ActorStorageApproach {
    private ActorStorageApproach() { }
    public static Optional<ShipmentEndpoint> select(FrontierWorldState state, ShipmentEndpoint endpoint,
                                                   SubjectId actorId, SurfaceAnchor origin) {
        return select(state, endpoint, actorId, origin, ShipmentEndpointComposition.knowledge(state, java.util.List.of(endpoint)));
    }
    public static Optional<ShipmentEndpoint> select(FrontierWorldState state, ShipmentEndpoint endpoint,
            SubjectId actorId, SurfaceAnchor origin, KnownPedestrianRouteKnowledge declaredJourney) {
        ShipmentEndpointComposition.validate(state, endpoint);
        var knowledge = declaredJourney.forActor(state, actorId);
        var candidates = java.util.stream.Stream.concat(java.util.stream.Stream.of(endpoint.station()),
                ShipmentEndpointComposition.stations(state, endpoint).stream()).distinct().toList();
        KnownPedestrianNavigation.RouteUnavailable pending = null;
        for (var station : candidates) {
            if (!knowledge.clearStation(station)) continue;
            var selected = endpoint.withStation(station);
            ShipmentEndpointComposition.validate(state, selected);
            try { knowledge.plannedBetween(origin, station); return Optional.of(selected); }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) pending = unavailable;
            }
        }
        if (pending != null) throw pending;
        return Optional.empty();
    }
}
