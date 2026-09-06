package io.farfrontier.palemirror.frontier.v3.model;

/** Persisted progress for one exact resident service operation. */
public enum SettlementServiceWorkPhase {
    PREPARED(1),
    APPROACH_INPUT(2),
    INPUT_ISSUE_PENDING(3),
    APPROACH_WORK(4),
    WORKING(5),
    EFFECT_READY(6),
    BLOCKED(7),
    UNKNOWN_AFTER_RESTART(8),
    COMPLETED(9);

    private final int wireTag;

    SettlementServiceWorkPhase(int wireTag) { this.wireTag = wireTag; }

    public int wireTag() { return wireTag; }

    public boolean active() {
        return this == PREPARED || this == APPROACH_INPUT || this == INPUT_ISSUE_PENDING || this == APPROACH_WORK
                || this == WORKING || this == EFFECT_READY || this == UNKNOWN_AFTER_RESTART;
    }

    public static SettlementServiceWorkPhase fromWireTag(int wireTag) {
        for (SettlementServiceWorkPhase value : values()) if (value.wireTag == wireTag) return value;
        throw new IllegalArgumentException("unknown settlement service-work phase tag: " + wireTag);
    }
}
