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
}
