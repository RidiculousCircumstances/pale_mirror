package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import java.util.*;

/** Known baseline invalidations. A foreign mutation grants no right to mine or restore its replacement. */
public record KnownBlockExclusions(Set<BlockPosition> positions) {
    public static final int MAX_POSITIONS = 32_768;
    public KnownBlockExclusions {
        positions = Set.copyOf(positions);
        if (positions.size() > MAX_POSITIONS) throw new IllegalArgumentException("known geometry invalidation capacity exhausted");
    }
    public static KnownBlockExclusions empty() { return new KnownBlockExclusions(Set.of()); }
    public KnownBlockExclusions invalidate(BlockPosition position) {
        if (positions.contains(position)) return this;
        var next = new HashSet<>(positions); next.add(position); return new KnownBlockExclusions(next);
    }
}
