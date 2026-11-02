package io.farfrontier.palemirror.frontier.v3.model.combat;

import io.farfrontier.palemirror.frontier.v3.model.SurfaceAnchor;
import java.util.Objects;

/** Explicit tactical purpose and spatial limit; neither movement nor hit permission. */
public record CombatEngagement(CombatTarget target, Kind kind, SurfaceAnchor anchor, int radius) {
    public enum Kind {
        SELF_DEFENCE(1), ALLY_DEFENCE(2), AREA_DEFENCE(3), REINFORCEMENT(4);
        private final int tag;
        Kind(int tag) { this.tag = tag; }
        public int wireTag() { return tag; }
        public boolean local() { return this != REINFORCEMENT; }
        public static Kind require(int tag) {
            for (var kind : values()) if (kind.tag == tag) return kind;
            throw new IllegalArgumentException("unknown combat engagement kind");
        }
    }
    public CombatEngagement {
        Objects.requireNonNull(target); Objects.requireNonNull(kind); Objects.requireNonNull(anchor);
        if (radius < 1 || radius > 4096) throw new IllegalArgumentException("invalid combat engagement envelope");
    }
    public boolean contains(SurfaceAnchor position) { return anchor.manhattanDistance(position) <= radius; }
}
