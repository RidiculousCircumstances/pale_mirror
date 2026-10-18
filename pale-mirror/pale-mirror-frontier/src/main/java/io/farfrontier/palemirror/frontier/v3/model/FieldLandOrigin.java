package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** Explicit initial projection authority: authored fixture land differs from a surveyed runtime acquisition. */
public record FieldLandOrigin(Kind kind, Map<ResourceFieldLayout.CellId, ResourceFieldCycle.Soil> ground) {
    public enum Kind {
        AUTHORED(1), SURVEYED(2);
        private final int tag;
        Kind(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public static Kind decode(int tag) {
            return switch (tag) { case 1 -> AUTHORED; case 2 -> SURVEYED; default -> throw new IllegalArgumentException("unknown field land origin"); };
        }
    }
    public FieldLandOrigin {
        Objects.requireNonNull(kind); ground = Map.copyOf(ground);
        if (kind == Kind.AUTHORED && !ground.isEmpty() || kind == Kind.SURVEYED && ground.isEmpty()
                || ground.size() > ResourceFieldLayout.MAX_CELLS || ground.values().stream().anyMatch(soil -> !soil.cultivable()))
            throw new IllegalArgumentException("invalid declared field land origin");
    }
}
