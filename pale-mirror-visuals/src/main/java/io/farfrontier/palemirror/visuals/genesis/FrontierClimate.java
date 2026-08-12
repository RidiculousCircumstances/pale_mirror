package io.farfrontier.palemirror.visuals.genesis;

public enum FrontierClimate {
    TEMPERATE("temperate_oak"),
    COLD_TAIGA("cold_spruce"),
    DRY_ARID("dry_acacia");

    private final String palette;

    FrontierClimate(String palette) { this.palette = palette; }

    public String palette() { return palette; }
}
