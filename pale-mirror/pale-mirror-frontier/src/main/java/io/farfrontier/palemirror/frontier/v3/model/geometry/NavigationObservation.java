package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;

/** Adapter evidence, including why no collision fact can currently be published.
 * Dependencies are exact bounded neighbour reads, not guessed block-type rules. */
public record NavigationObservation(Kind kind, Optional<NavigationCell> cell, Map<BlockPosition, Optional<NavigationBlockState>> dependencies) {
    public static final int DEPENDENCY_RADIUS = 1, MAX_DEPENDENCIES = 26;
    public enum Kind {
        KNOWN(1), WRITE_PENDING(2), NOT_OBSERVED(3), CHUNK_UNAVAILABLE(4),
        NEIGHBOUR_UNAVAILABLE(5), ENTITY_CONTEXT_REQUIRED(6), BLOCK_ENTITY_REQUIRED(7),
        SHAPE_LIMIT(8), POSITION_REQUIRED(9), DEPENDENCY_LIMIT(10), DYNAMIC_SHAPE_UNSUPPORTED(11);
        private final int tag;
        Kind(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Kind requireWireTag(int tag) {
            for (var kind : values()) if (kind.tag == tag) return kind;
            throw new IllegalArgumentException("unknown navigation evidence kind: " + tag);
        }
    }
    public NavigationObservation {
        Objects.requireNonNull(kind); Objects.requireNonNull(cell);
        dependencies = Map.copyOf(dependencies);
        if ((kind == Kind.KNOWN) != cell.isPresent() || dependencies.size() > MAX_DEPENDENCIES)
            throw new IllegalArgumentException("inconsistent navigation evidence");
        if (kind == Kind.KNOWN && dependencies.values().stream().anyMatch(Optional::isEmpty))
            throw new IllegalArgumentException("known shape depends on an unknown block");
    }
    public static NavigationObservation known(NavigationCell cell) { return known(cell, Map.of()); }
    public static NavigationObservation known(NavigationCell cell, Map<BlockPosition, Optional<NavigationBlockState>> dependencies) {
        return new NavigationObservation(Kind.KNOWN, Optional.of(cell), dependencies);
    }
    public static NavigationObservation unavailable(Kind kind) { return unavailable(kind, Map.of()); }
    public static NavigationObservation unavailable(Kind kind, Map<BlockPosition, Optional<NavigationBlockState>> dependencies) {
        return new NavigationObservation(kind, Optional.empty(), dependencies);
    }
    public void requireAt(BlockPosition origin) {
        for (var position : dependencies.keySet()) if (position.equals(origin)
                || Math.abs((long) position.x() - origin.x()) > DEPENDENCY_RADIUS
                || Math.abs((long) position.y() - origin.y()) > DEPENDENCY_RADIUS
                || Math.abs((long) position.z() - origin.z()) > DEPENDENCY_RADIUS)
            throw new IllegalArgumentException("navigation dependency outside bounded observation");
    }
}
