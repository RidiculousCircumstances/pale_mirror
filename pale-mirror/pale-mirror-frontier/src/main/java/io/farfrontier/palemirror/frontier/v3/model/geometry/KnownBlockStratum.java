package io.farfrontier.palemirror.frontier.v3.model.geometry;

import io.farfrontier.palemirror.frontier.v3.model.BlockPosition;
import io.farfrontier.palemirror.frontier.v3.model.WorldBounds;
import java.util.Objects;
import java.util.Optional;

/** Explicit content fact, independent of any work-cell plan. No procedural unknown-rock inference. */
public record KnownBlockStratum(int minY, int maxY, String blockKind, long revision) {
    public KnownBlockStratum {
        Objects.requireNonNull(blockKind);
        if (minY > maxY || minY < -2048 || maxY > 2047 || revision < 1 || !blockKind.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("invalid known geological stratum");
    }
    public Optional<String> at(WorldBounds bounds, BlockPosition position) {
        return bounds.contains(position) && position.y() >= minY && position.y() <= maxY
                ? Optional.of(blockKind) : Optional.empty();
    }
    public String canonicalText() { return minY + ":" + maxY + ":" + blockKind + ":" + revision; }
}
