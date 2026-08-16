package io.farfrontier.palemirror.api;

import java.util.Objects;

public record VisualBounds(VisualPoint min, VisualPoint max) {
    public VisualBounds {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
            throw new IllegalArgumentException("Visual bounds must be ordered");
        }
    }

    public boolean contains(VisualPoint point) {
        Objects.requireNonNull(point, "point");
        return point.x() >= min.x() && point.x() <= max.x()
                && point.y() >= min.y() && point.y() <= max.y()
                && point.z() >= min.z() && point.z() <= max.z();
    }

    /** Horizontal footprint test used when authored discovery is chunk-addressed. */
    public boolean intersectsChunk(VisualChunk chunk) {
        Objects.requireNonNull(chunk, "chunk");
        int minChunkX = Math.floorDiv(min.x(), 16);
        int maxChunkX = Math.floorDiv(max.x(), 16);
        int minChunkZ = Math.floorDiv(min.z(), 16);
        int maxChunkZ = Math.floorDiv(max.z(), 16);
        return chunk.x() >= minChunkX && chunk.x() <= maxChunkX
                && chunk.z() >= minChunkZ && chunk.z() <= maxChunkZ;
    }
}
