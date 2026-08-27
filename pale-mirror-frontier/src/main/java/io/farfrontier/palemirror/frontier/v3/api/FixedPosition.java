package io.farfrontier.palemirror.frontier.v3.api;

import java.util.Objects;

/** Exact block-space position independent of Minecraft vector classes. */
public record FixedPosition(FixedScalar x, FixedScalar y, FixedScalar z) {
    public FixedPosition {
        Objects.requireNonNull(x, "x");
        Objects.requireNonNull(y, "y");
        Objects.requireNonNull(z, "z");
    }
}
