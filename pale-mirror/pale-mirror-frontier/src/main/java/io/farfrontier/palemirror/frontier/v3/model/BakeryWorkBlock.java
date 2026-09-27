package io.farfrontier.palemirror.frontier.v3.model;

import io.farfrontier.palemirror.frontier.v3.api.SubjectId;
import java.util.Objects;

/** One retained, owner-local reason why the current bakery phase cannot advance. */
public record BakeryWorkBlock(Reason reason, SubjectId scopeId, int slot, String observedKind, int observedCount) {
    public enum Reason {
        SOURCE_CHANGED(1), HAND_MISMATCH(2), DESTINATION_OCCUPIED(3), ROUTE_BLOCKED(4),
        AMBIGUOUS_EFFECT(5), MACHINE_UNAVAILABLE(6);
        private final int wireTag;
        Reason(int wireTag) { this.wireTag = wireTag; }
        public int wireTag() { return wireTag; }
        public static Reason fromWireTag(int tag) {
            for (Reason reason : values()) if (reason.wireTag == tag) return reason;
            throw new IllegalArgumentException("unknown bakery block reason tag: " + tag);
        }
    }

    public BakeryWorkBlock {
        Objects.requireNonNull(reason, "bakery block reason");
        Objects.requireNonNull(scopeId, "bakery block scope");
        Objects.requireNonNull(observedKind, "bakery observed item kind");
        if (slot < -1 || slot > 53 || observedCount < 0 || observedCount > 127
                || !observedKind.matches("[a-z][a-z0-9_-]{0,31}:[a-z0-9][a-z0-9_./-]{0,127}"))
            throw new IllegalArgumentException("bakery block needs a bounded physical observation");
    }
}
