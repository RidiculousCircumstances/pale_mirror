package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.Set;

/** Immutable obstacle facts. Point predicates supply no proof of an empty area;
 * an indexed producer may explicitly supply both capabilities from the same set. */
@FunctionalInterface
public interface NavigationObstacleView extends java.util.function.Predicate<BlockPosition> {
    default boolean emptyArea(WorldBounds area) { return false; }
    static NavigationObstacleView indexed(Set<BlockPosition> positions) {
        var cells = RegionalBlockSet.copyOf(positions);
        return new NavigationObstacleView() {
            @Override public boolean test(BlockPosition position) { return cells.contains(position); }
            @Override public boolean emptyArea(WorldBounds area) { return !NavigationRegionCoverage.intersects(cells.regions(), area); }
        };
    }
}
