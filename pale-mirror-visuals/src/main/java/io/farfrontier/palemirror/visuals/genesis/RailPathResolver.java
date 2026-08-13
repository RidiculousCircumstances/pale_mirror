package io.farfrontier.palemirror.visuals.genesis;

import io.farfrontier.palemirror.api.VisualPoint;
import java.util.List;

@FunctionalInterface
public interface RailPathResolver {
    List<VisualPoint> resolve(RoutePlacementRequirement requirement, VisualPoint from, VisualPoint to);
}
