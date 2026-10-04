package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.model.navigation.KnownPedestrianNavigation;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import io.farfrontier.palemirror.frontier.v3.model.navigation.TraversalRejoin;
import java.util.List;
import java.util.Optional;

/** Transit owns the unchanged journey checkpoint; shared geometry owns its known approach. */
public final class ResidentMigrationJourneyKnowledge {
    private ResidentMigrationJourneyKnowledge() { }
    public static MovementOrder order(ResidentMigrationJourney journey, long revision) {
        return new MovementOrder(journey.residentId(), journey.residentId(), journey.routeIndex(), revision,
                List.of(new SurfaceAnchor(journey.nextColdPosition())), TraversalCapability.PEDESTRIAN,
                MovementOrder.ArrivalPolicy.EXACT_STATION);
    }
    public static StationApproachState checkpoint(FrontierWorldState state, ResidentMigrationJourney journey, SurfaceAnchor observed) {
        long revision = Math.incrementExact(journey.routeRevision());
        try {
            var path = KnownPedestrianRouteKnowledge.forFrontier(state).path(observed, order(journey, revision));
            return new StationApproachState(revision, Optional.of(new TraversalRejoin(path, 0)), Optional.empty());
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            return new StationApproachState(revision, Optional.empty(), Optional.of(observed));
        }
    }
    public static Optional<TraversalRejoin> approach(FrontierWorldState state, ResidentMigrationJourney journey) {
        return journey.spatial().knownApproach(origin -> checkpoint(state, journey, origin).approach());
    }
}
