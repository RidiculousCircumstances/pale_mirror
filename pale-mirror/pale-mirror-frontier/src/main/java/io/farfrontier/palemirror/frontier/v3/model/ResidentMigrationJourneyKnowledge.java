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
    public static StationApproachState checkpoint(ResidentMigrationJourney journey, SurfaceAnchor observed) {
        return journey.spatial().observedAt(observed);
    }
    public static Optional<TraversalRejoin> approach(FrontierWorldState state, ResidentMigrationJourney journey) {
        try {
            return StationApproachNavigation.plan(KnownPedestrianRouteKnowledge.forFrontier(state),
                    journey.spatial(), order(journey, journey.routeRevision()));
        } catch (KnownPedestrianNavigation.RouteUnavailable unavailable) {
            if (unavailable.status() != io.farfrontier.palemirror.frontier.v3.model.navigation.PedestrianRouteResult.Status.NO_PATH)
                throw unavailable;
            return Optional.empty();
        }
    }
}
