package io.farfrontier.palemirror.frontier.v3.model;

/** Persisted progress for one exact resident service operation. */
public enum SettlementServiceWorkPhase {
    PREPARED(1),
    APPROACH(2),
    WORKING(3),
    EFFECT_READY(4),
    BLOCKED(5),
    UNKNOWN_AFTER_RESTART(6),
    COMPLETED(7);

    private final int wireTag;

    SettlementServiceWorkPhase(int wireTag) { this.wireTag = wireTag; }

    public int wireTag() { return wireTag; }

    public boolean active() {
        return this == PREPARED || this == APPROACH || this == WORKING || this == EFFECT_READY || this == UNKNOWN_AFTER_RESTART;
    }

    public static SettlementServiceWorkPhase fromWireTag(int wireTag) {
        for (SettlementServiceWorkPhase value : values()) if (value.wireTag == wireTag) return value;
        throw new IllegalArgumentException("unknown settlement service-work phase tag: " + wireTag);
    }
}
