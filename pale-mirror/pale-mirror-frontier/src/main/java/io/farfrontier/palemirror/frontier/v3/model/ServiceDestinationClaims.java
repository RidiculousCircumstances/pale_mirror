package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.HashSet;
import java.util.Set;

/** Read-only final-position claims, not terrain obstacles or another movement owner. */
public final class ServiceDestinationClaims {
    private ServiceDestinationClaims() { }

    public static Set<SurfaceAnchor> excludedFor(FrontierWorldState state, SubjectId actorId) {
        Set<SurfaceAnchor> excluded = new HashSet<>();
        state.actorLocations().forEach((id, actor) -> {
            if (!id.equals(actorId) && actor.condition().status() == ActorLifeStatus.ALIVE)
                excluded.add(actor.supportingSurface());
        });
        state.humanPopulation().meals().values().stream()
                .filter(meal -> !meal.residentId().equals(actorId)).forEach(meal -> {
                    excluded.add(meal.clearingSurface());
                });
        state.actorMovements().values().stream()
                .filter(movement -> !movement.order().actorId().equals(actorId))
                .flatMap(movement -> movement.order().legalStations().stream()).forEach(excluded::add);
        return Set.copyOf(excluded);
    }
}
