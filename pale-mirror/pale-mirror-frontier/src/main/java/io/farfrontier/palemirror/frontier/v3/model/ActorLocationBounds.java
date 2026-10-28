package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Map;
import java.util.function.BiFunction;

/** Position-owner bounds proof for the active immutable actor register. */
final class ActorLocationBounds {
    private static final ImmutableRegistryView<SubjectId, ActorLocation, Boolean> VALID =
            new ImmutableRegistryView<>(SimulationCapacity.MAX_ACTORS);
    private ActorLocationBounds() { }
    static void validate(WorldBounds bounds, Map<SubjectId, ActorLocation> actors) {
        BiFunction<SubjectId, ActorLocation, Boolean> check = (id, actor) -> {
            FrontierWorldStateSupport.requirePosition(bounds, actor.supportingSurface().support()); return Boolean.TRUE;
        };
        if (ImmutableInvariantCheck.fullAudit()) VALID.project(bounds, actors, (before, after) -> false, check);
        else VALID.project(bounds, actors, check);
    }
}
