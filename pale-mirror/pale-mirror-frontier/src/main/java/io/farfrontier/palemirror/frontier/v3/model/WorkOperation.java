package io.farfrontier.palemirror.frontier.v3.model;

/** Stable operation identity, independent of resource identity and navigation. */
public enum WorkOperation {
    HARVEST(1), SOW(2), EXTRACT(4), PRODUCE(5), TILL(6);
    private final int tag;
    WorkOperation(int tag) { this.tag = tag; }
    public int wireTag() { return tag; }
    public static WorkOperation fromWireTag(int tag) {
        return switch (tag) {
            case 1 -> HARVEST; case 2 -> SOW; case 4 -> EXTRACT; case 5 -> PRODUCE; case 6 -> TILL;
            default -> throw new IllegalArgumentException("unknown work operation tag");
        };
    }
}
