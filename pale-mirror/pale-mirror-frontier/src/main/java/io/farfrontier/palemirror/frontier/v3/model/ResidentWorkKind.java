package io.farfrontier.palemirror.frontier.v3.model;

/** Explicit permission keys; vocational affinity never supplies a permission or dispatch key. */
public enum ResidentWorkKind {
    AGRICULTURE(1), BAKING(2), LOGISTICS(3);

    private final int wireTag;
    ResidentWorkKind(int wireTag) { this.wireTag = wireTag; }
    public int wireTag() { return wireTag; }
    public static ResidentWorkKind fromWireTag(int tag) {
        for (ResidentWorkKind kind : values()) if (kind.wireTag == tag) return kind;
        throw new IllegalArgumentException("unknown resident work permission tag: " + tag);
    }
}
