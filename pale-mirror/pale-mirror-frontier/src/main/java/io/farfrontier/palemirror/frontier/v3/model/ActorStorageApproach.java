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
        for (var station : candidates) {
            if (!knowledge.clearStation(station)) continue;
            var selected = endpoint.withStation(station);
            ShipmentEndpointComposition.validate(state, selected);
            // Admission needs connected endpoints, not a block-by-block itinerary.
            // The movement owner refines bounded segments after a trip is selected.
            try { knowledge.estimateBetween(origin, station); return Optional.of(selected); }
            catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
                // An unfinished proof does not disqualify this station. Retain
                // one addressed wait instead of racing every port's full route.
                if (unavailable.status() != PedestrianRouteResult.Status.NO_PATH) throw unavailable;
            }
        }
        return Optional.empty();
    }
}
