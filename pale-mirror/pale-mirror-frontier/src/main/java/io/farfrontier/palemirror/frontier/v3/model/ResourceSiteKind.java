package io.farfrontier.palemirror.frontier.v3.model;

/** Stable source type; later renewable and extractive sites share this identity boundary. */
public enum ResourceSiteKind {
    WHEAT_FIELD(1, 64, "minecraft:wheat");

    private final int wireTag;
    private final int cropSlotCount;
    private final String resourceKind;
    ResourceSiteKind(int wireTag, int cropSlotCount, String resourceKind) {
        this.wireTag = wireTag;
        this.cropSlotCount = cropSlotCount;
        this.resourceKind = resourceKind;
    }
    public String resourceKind() { return resourceKind; }
    public int wireTag() { return wireTag; }
    public static ResourceSiteKind fromWireTag(int tag) {
        if (tag == 1) return WHEAT_FIELD;
        throw new IllegalArgumentException("unknown resource site kind: " + tag);
    }
    public int cropSlotCount() { return cropSlotCount; }
}
