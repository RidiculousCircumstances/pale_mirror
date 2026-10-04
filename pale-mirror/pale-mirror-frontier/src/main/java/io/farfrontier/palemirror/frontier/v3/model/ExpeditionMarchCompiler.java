package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Admission-only legal geometry for the declared cohort; no new tactics, positions or authority. */
public final class ExpeditionMarchCompiler {
    private ExpeditionMarchCompiler() { }
    public static ExpeditionMarch compile(FrontierWorldState state, SubjectId owner, SubjectId overseer,
                                           Map<SubjectId, SurfaceAnchor> starts, Map<SubjectId, SurfaceAnchor> destinations) {
        starts = Map.copyOf(Objects.requireNonNull(starts, "expedition origins"));
        destinations = Map.copyOf(Objects.requireNonNull(destinations, "expedition stations"));
        if (starts.isEmpty() || starts.size() > ExpeditionMarch.MAX_MEMBERS || !starts.containsKey(overseer)
                || !starts.keySet().equals(destinations.keySet())
                || starts.values().stream().distinct().count() != starts.size()
                || destinations.values().stream().distinct().count() != destinations.size())
            throw new IllegalArgumentException("expedition geometry must declare distinct endpoints for its exact cohort");
        var topologies = new LinkedHashMap<SubjectId, TraversalTopology>();
        var retainedCorridors = new java.util.ArrayList<List<SurfaceAnchor>>();
        for (var actor : starts.keySet().stream().sorted().toList()) {
            var reserved = new LinkedHashSet<SurfaceAnchor>();
            destinations.forEach((peer, surface) -> { if (!peer.equals(actor)) reserved.add(surface); });
            int horizon = retainedCorridors.stream().mapToInt(List::size).max().orElse(0);
            var path = HiveGroundNavigation.route(state, actor, starts.get(actor), destinations.get(actor), reserved,
                    horizon, (from, to, step) -> retainedCorridors.stream().noneMatch(prior -> {
                        var peerFrom = prior.get(Math.min(step - 1, prior.size() - 1));
                        var peerTo = prior.get(Math.min(step, prior.size() - 1));
                        return to.equals(peerTo) || from.equals(peerTo) && to.equals(peerFrom);
                    }));
            if (path.size() > TraversalTopology.MAX_NODES)
                throw new HiveGroundNavigation.RouteUnavailable("expedition route exceeds its retained topology bound");
            // Time-indexed reservations prevent shared-cursor collisions and head-on swaps;
            // another member's entire trail must never become a permanent terrain wall.
            retainedCorridors.add(path);
            topologies.put(actor, TraversalTopology.corridor(new TraversalTopologyId("topology:expedition:"
                    + owner.value() + ":" + actor.value()), 1L, owner, TraversalKind.GROUND_BIOFORM,
                    Set.of(TraversalCapability.GROUND_BIOFORM), path));
        }
        return new ExpeditionMarch(overseer, 0, topologies);
    }
}
