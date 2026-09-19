package io.farfrontier.palemirror.frontier.v3.model;

/**
 * Stable, producer-declared authority vocabulary for a known physical loss.
 * Tags are deliberately non-ordinal: removed tags must never be reused.
 */
public enum PhysicalDeltaSemanticTargetKind {
    SETTLEMENT_STRUCTURE(1),
    HIVE_ORGAN(2),
    HIVE_COCOON(3),
    ROUTE_NETWORK(4),
    ROUTE_CONSTRUCTION(5),
    SETTLEMENT_INFRASTRUCTURE(6);

    private final int wireTag;

    PhysicalDeltaSemanticTargetKind(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }

    public static PhysicalDeltaSemanticTargetKind fromWireTag(int wireTag) {
        for (PhysicalDeltaSemanticTargetKind value : values()) if (value.wireTag == wireTag) return value;
        throw new IllegalArgumentException("unknown physical-delta semantic target tag: " + wireTag);
    }
}
