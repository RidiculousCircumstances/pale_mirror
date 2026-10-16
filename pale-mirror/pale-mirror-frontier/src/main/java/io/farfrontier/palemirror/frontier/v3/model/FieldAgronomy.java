package io.farfrontier.palemirror.frontier.v3.model;

/** Crop adapter to territorial conditions; no inventory, scheduling or physical mutation authority. */
public final class FieldAgronomy {
    private FieldAgronomy() { }
    public static int yield(FrontierBootstrap bootstrap, BlockPosition crop) {
        return bootstrap.ruleset().fields().agriculture()
                .map(rules -> rules.yield(FieldHarvestExtraction.ITEM, crop.x(), crop.z())).orElse(1);
    }
    public static int growthPeriods(FrontierBootstrap bootstrap, BlockPosition crop) {
        return bootstrap.ruleset().fields().agriculture()
                .map(rules -> rules.environmentAt(crop.x(), crop.z()).growthPeriods()).orElse(1);
    }
    /** A later route retarget may choose another biome, so leave room for any declared cell yield. */
    public static int maximumYield(FrontierBootstrap bootstrap) {
        return bootstrap.ruleset().fields().agriculture().map(rules -> rules.biomes().keySet().stream()
                .mapToInt(biome -> rules.yieldInBiome(FieldHarvestExtraction.ITEM, biome)).max().orElseThrow()).orElse(1);
    }
    public static ResourceFieldCycle grow(FrontierWorldState state, ResourceFieldCycle cycle, long atTick) {
        long interval = state.bootstrap().ruleset().cadence().resourceGrowthStageInterval();
        return cycle.advanceGrowthStage(state.resourceSites().growthProtectedCells(cycle.siteId()),
                cell -> Math.floorDiv(atTick, interval) % growthPeriods(state.bootstrap(), cell.crop()) == 0);
    }
    public static long fullGrowthTicks(FrontierBootstrap bootstrap, ResourceFieldLayout layout) {
        int longest = layout.cells().stream().mapToInt(cell -> growthPeriods(bootstrap, cell.crop())).max().orElseThrow();
        return Math.multiplyExact(Math.multiplyExact(longest, ResourceSiteLifecycle.MATURE_STAGE),
                bootstrap.ruleset().cadence().resourceGrowthStageInterval());
    }
    public static long expectedYield(FrontierBootstrap bootstrap, ResourceFieldCycle cycle) {
        return cycle.layout().cells().stream().filter(cell -> {
            var soil = cycle.cell(cell.id()).soil();
            return soil == ResourceFieldCycle.Soil.FARMLAND || soil == ResourceFieldCycle.Soil.DIRT;
        }).mapToLong(cell -> FieldAgronomy.yield(bootstrap, cell.crop())).sum();
    }
}
