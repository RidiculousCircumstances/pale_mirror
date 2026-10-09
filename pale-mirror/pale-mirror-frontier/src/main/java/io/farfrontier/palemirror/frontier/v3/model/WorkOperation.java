package io.farfrontier.palemirror.frontier.v3.model;

/** Stable operation identity, independent of resource identity and navigation. */
public enum WorkOperation {
    HARVEST(1), SOW(2), TILL_AND_SOW(3), EXTRACT(4);
    private final int tag;
    WorkOperation(int tag) { this.tag = tag; }
    public int wireTag() { return tag; }
    public static WorkOperation fromWireTag(int tag) {
        return switch (tag) {
            case 1 -> HARVEST; case 2 -> SOW; case 3 -> TILL_AND_SOW; case 4 -> EXTRACT;
            default -> throw new IllegalArgumentException("unknown work operation tag");
        };
    }
}
