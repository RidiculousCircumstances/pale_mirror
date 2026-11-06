package io.farfrontier.palemirror.frontier.v3.model;

/** Producer-declared ordinary storage provider, independent of its economic policy. */
public enum StorageBlockKind {
    CHEST(1), BARREL(2), RACK(3);
    private final int tag;
    StorageBlockKind(int tag) { this.tag = tag; }
    public int wireTag() { return tag; }
    public static StorageBlockKind decode(int tag) {
        return switch (tag) {
            case 1 -> CHEST;
            case 2 -> BARREL;
            case 3 -> RACK;
            default -> throw new IllegalArgumentException("unknown ordinary storage provider");
        };
    }
}
