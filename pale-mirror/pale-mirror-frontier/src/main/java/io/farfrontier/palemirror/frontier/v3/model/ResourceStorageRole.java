package io.farfrontier.palemirror.frontier.v3.model;

/** Explicit placement tier, not inferred from container purpose or location. */
public enum ResourceStorageRole {
    WAREHOUSE(1), LOCAL_BUFFER(2);
    private final int tag;
    ResourceStorageRole(int tag) { this.tag = tag; }
    public int wireTag() { return tag; }
    public static ResourceStorageRole decode(int tag) {
        return switch (tag) {
            case 1 -> WAREHOUSE;
            case 2 -> LOCAL_BUFFER;
            default -> throw new IllegalArgumentException("unknown storage role");
        };
    }
}
