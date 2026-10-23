package io.farfrontier.palemirror.frontier.v3.model;

/** Producer-declared station client. Never recovered from recipe, ID spelling or job membership. */
public enum ProductionOrderKind {
    STATION_WORK(1);
    private final int wireTag;
    ProductionOrderKind(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
    public static ProductionOrderKind fromWireTag(int tag) {
        for (var value : values()) if (value.wireTag == tag) return value;
        throw new IllegalArgumentException("unknown production order owner tag: " + tag);
    }
}
