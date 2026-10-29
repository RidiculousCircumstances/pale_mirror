package io.farfrontier.palemirror.frontier.v3.model.expedition;

import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.group.GroupRendezvous;
import java.util.ArrayList;

/** Cheap necessary travel bound for any declared pedestrian expedition. It
 * needs no geometry construction/search and proves neither a route nor arrival. */
public final class ExpeditionJourneyBounds {
    public static long minimumDuration(FrontierWorldState state, ShipmentEndpoint sender, ShipmentEndpoint receiver) {
        var from = new ArrayList<>(ShipmentEndpointComposition.stations(state, sender));
        from.add(sender.station());
        var to = new ArrayList<>(ShipmentEndpointComposition.stations(state, receiver));
        to.add(receiver.station());
        long edges = GroupRendezvous.minimumHorizontalSeparation(from, to);
        // A weak bound for nearby endpoints avoids pretending that a guessed
        // assembly station or provisional distance is the actual shortest trip.
        return edges == 0 ? 0 : state.bootstrap().ruleset().expedition().plannedDuration(edges, edges);
    }
    private ExpeditionJourneyBounds() { }
}
