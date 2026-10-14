package io.farfrontier.palemirror.frontier.v3.model.navigation;

import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.Map;

/** All surveyed regions, including negative/unknown search branches, not merely the found path. */
record PedestrianRegionDependencies(WorldBounds bounds, Object scope, Object geometryScope, Map<PedestrianRegion.Tile, Object> revisions) {
    PedestrianRegionDependencies { revisions = Map.copyOf(revisions); }
    boolean matches(PedestrianRouteGeometry geometry) {
        var current = geometry.regionalRevision();
        return bounds.equals(geometry.bounds()) && current.isPresent() && current.orElseThrow().scope().equals(scope)
                && current.orElseThrow().geometryScope().equals(geometryScope)
                && revisions.entrySet().stream().allMatch(entry -> java.util.Objects.equals(entry.getValue(),
                        current.orElseThrow().at(entry.getKey().x(), entry.getKey().z())));
    }
}
