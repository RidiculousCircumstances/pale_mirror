package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.navigation.MovementOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Transit chooses its strategic anchors; shared navigation owns support and obstacle clearance. */
public final class FrontierMigrationCorridor {
    private FrontierMigrationCorridor() { }

    public static List<BlockPosition> compile(FrontierWorldState state, SubjectId residentId, BlockPosition start,
            Settlement source, Settlement destination, BlockPosition arrival) {
        Objects.requireNonNull(state); Objects.requireNonNull(residentId);
        Objects.requireNonNull(start); Objects.requireNonNull(source);
        Objects.requireNonNull(destination); Objects.requireNonNull(arrival);
        // Other pedestrians are transient traffic, not permanent terrain along
        // a strategic trip. Shared HOT navigation/access owns their actual interaction.
        var knowledge = KnownPedestrianRouteKnowledge.forFrontier(state).forActor(state, residentId);
        var anchors = new ArrayList<BlockPosition>();
        var outward = state.routeTopology().settlementWaypoints(state.bootstrap(), source.id());
        for (int index = 1; index < outward.size(); index++) anchors.add(outward.get(index));
        var inward = state.routeTopology().settlementWaypoints(state.bootstrap(), destination.id());
        for (int index = inward.size() - 1; index >= 1; index--) anchors.add(inward.get(index));
        anchors.add(arrival);
        var route = new ArrayList<SurfaceAnchor>(); route.add(new SurfaceAnchor(start));
        for (int ordinal = 0; ordinal < anchors.size(); ordinal++) {
            var target = new SurfaceAnchor(anchors.get(ordinal));
            var order = new MovementOrder(residentId, residentId, ordinal, 1L, List.of(target),
                    TraversalCapability.PEDESTRIAN, MovementOrder.ArrivalPolicy.EXACT_STATION);
            var segment = knowledge.path(route.getLast(), order);
            if (route.size() + segment.size() - 1 > ResidentMigrationJourney.MAX_WAYPOINTS)
                throw new IllegalArgumentException("migration route exceeds bounded frontier profile");
            route.addAll(segment.subList(1, segment.size()));
        }
        return route.stream().map(SurfaceAnchor::support).toList();
    }
}
