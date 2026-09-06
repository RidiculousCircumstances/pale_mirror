package io.farfrontier.palemirror.frontier.v3.model;

/** Stable domain form for a resident-owned settlement service operation. */
public enum SettlementServiceWorkKind {
    STRUCTURAL_REPAIR(1),
    DECONTAMINATION(2);

    private final int wireTag;

    SettlementServiceWorkKind(int wireTag) { this.wireTag = wireTag; }

    public int wireTag() { return wireTag; }

    public static SettlementServiceWorkKind fromWireTag(int wireTag) {
        for (SettlementServiceWorkKind value : values()) if (value.wireTag == wireTag) return value;
        throw new IllegalArgumentException("unknown settlement service-work kind tag: " + wireTag);
    }
}
