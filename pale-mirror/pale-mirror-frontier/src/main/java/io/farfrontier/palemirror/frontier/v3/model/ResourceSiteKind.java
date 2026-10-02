package io.farfrontier.palemirror.frontier.v3.model;

/** Stable source type; later renewable and extractive sites share this identity boundary. */
public enum ResourceSiteKind {
    WHEAT_FIELD(64, "minecraft:wheat");

    private final int cropSlotCount;
    private final String resourceKind;
    ResourceSiteKind(int cropSlotCount, String resourceKind) {
        this.cropSlotCount = cropSlotCount;
        this.resourceKind = resourceKind;
    }
    public String resourceKind() { return resourceKind; }
    public int cropSlotCount() { return cropSlotCount; }
}
