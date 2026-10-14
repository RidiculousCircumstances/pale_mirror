package io.farfrontier.palemirror.internal.frontier.v3;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import io.farfrontier.palemirror.frontier.v3.model.*;
import io.farfrontier.palemirror.frontier.v3.model.navigation.*;
import java.util.*;

/** Read-only composition of canonical body/movement/meal projections for presentation demand. */
final class FrontierV3ActorSpatialCandidates {
    private record Row(BodyPosition body, List<SurfaceAnchor> segment) { }
    private final PedestrianSpatialIndex index = new PedestrianSpatialIndex();
    private final Map<SubjectId, Row> rows = new HashMap<>();
    private Object locations, movements, meals;

    void synchronize(FrontierWorldState state) {
        if (locations == state.actorLocations() && movements == state.actorMovements() && meals == state.humanPopulation().meals()) return;
        var retired = rows.keySet().iterator();
        while (retired.hasNext()) {
            var actor = retired.next();
            if (!state.actorLocations().containsKey(actor)) { index.remove(actor); retired.remove(); }
        }
        for (var entry : state.actorLocations().entrySet()) {
            var actor = entry.getKey(); var body = entry.getValue().body();
            var movement = state.actorMovements().get(actor);
            var meal = state.humanPopulation().meals().get(actor);
            var segment = movement != null ? movement.coldTravel().map(TimedKnownRoute::route).orElse(List.of())
                    : meal != null ? meal.coldTravel().map(TimedKnownRoute::route).orElse(List.of()) : List.<SurfaceAnchor>of();
            var previous = rows.get(actor);
            if (previous != null && previous.body().equals(body) && previous.segment() == segment) continue;
            index.replace(actor, body, segment); rows.put(actor, new Row(body, segment));
        }
        locations = state.actorLocations(); movements = state.actorMovements(); meals = state.humanPopulation().meals();
    }
    List<SubjectId> near(int x, int z, int radius) { return index.candidates(x, z, radius); }
}
