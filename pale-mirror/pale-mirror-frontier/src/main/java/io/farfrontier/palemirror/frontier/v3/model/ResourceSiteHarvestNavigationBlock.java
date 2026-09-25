package io.farfrontier.palemirror.frontier.v3.model;

import java.util.Objects;

/** One local farmer movement goal that cannot currently be reached; no crop outcome is implied. */
public record ResourceSiteHarvestNavigationBlock(SurfaceAnchor target, long layoutRevision, Reason reason) {
    public enum Reason {
        TARGET_SUPPORT(1), TARGET_CLEARANCE(2), TARGET_MEDIUM(3),
        PATH_UNAVAILABLE(4), PATH_STALLED(5), TARGET_CHUNK_UNLOADED(6),
        CONTINUATION_UNAVAILABLE(7), OFF_CONTRACT(8), UNSUPPORTED_CAPABILITY(9),
        KNOWN_GEOMETRY_UNAVAILABLE(10);

        private final int wireTag;
        Reason(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Reason requireWireTag(int tag) {
            for (Reason value : values()) if (value.wireTag == tag) return value;
            throw new IllegalArgumentException("unknown farmer navigation block reason");
        }
    }

    public ResourceSiteHarvestNavigationBlock {
        Objects.requireNonNull(target, "blocked farmer goal");
        Objects.requireNonNull(reason, "blocked farmer goal reason");
        if (layoutRevision < 1)
            throw new IllegalArgumentException("blocked farmer goal has invalid layout revision");
    }
}
