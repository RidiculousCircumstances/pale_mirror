package io.farfrontier.palemirror.frontier.v3.model;

/** Stable source type; later renewable and extractive sites share this identity boundary. */
public enum ResourceSiteKind {
    WHEAT_FIELD(64);

    private final int cropSlotCount;
    ResourceSiteKind(int cropSlotCount) { this.cropSlotCount = cropSlotCount; }
    public int cropSlotCount() { return cropSlotCount; }
}
