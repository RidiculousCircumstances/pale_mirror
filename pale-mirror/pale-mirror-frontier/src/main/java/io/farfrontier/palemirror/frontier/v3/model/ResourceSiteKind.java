package io.farfrontier.palemirror.frontier.v3.model;

/** Stable source type; later renewable and extractive sites share this identity boundary. */
public enum ResourceSiteKind {
    WHEAT_FIELD(1, new CropDefinition("pale_mirror:wheat", "minecraft:wheat", "minecraft:wheat",
            "minecraft:wheat_seeds", "pale_mirror:extraction/grain_harvest_v1", harvestTool())),
    COTTON_FIELD(2, new CropDefinition("pale_mirror:cotton", "pale_mirror:cotton_crop", "pale_mirror:cotton",
            "pale_mirror:cotton_seeds", "pale_mirror:extraction/cotton_harvest_v1", harvestTool()));

    private final int wireTag;
    private final CropDefinition crop;
    ResourceSiteKind(int wireTag, CropDefinition crop) {
        this.wireTag = wireTag;
        this.crop = crop;
    }
    public CropDefinition crop() { return crop; }
    public String resourceKind() { return crop.outputKind(); }
    public int wireTag() { return wireTag; }
    public static ResourceSiteKind fromWireTag(int tag) {
        for (var kind : values()) if (kind.wireTag == tag) return kind;
        throw new IllegalArgumentException("unknown resource site kind: " + tag);
    }
    public int cropSlotCount() { return 64; }
    public static java.util.Optional<ResourceSiteKind> forCropBlock(String block) {
        return java.util.Arrays.stream(values()).filter(kind -> kind.crop.blockKind().equals(block)).findFirst();
    }
    public static java.util.Optional<ResourceSiteKind> forResourceKind(String item) {
        return java.util.Arrays.stream(values()).filter(kind -> kind.resourceKind().equals(item)).findFirst();
    }
    private static io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement harvestTool() {
        return new io.farfrontier.palemirror.frontier.v3.model.tooling.ToolRequirement(
                io.farfrontier.palemirror.frontier.v3.model.tooling.ToolAbilities.HARVEST, 0, 1);
    }
}
