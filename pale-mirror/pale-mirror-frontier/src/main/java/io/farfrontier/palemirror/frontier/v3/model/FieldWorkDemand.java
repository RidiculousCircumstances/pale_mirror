package io.farfrontier.palemirror.frontier.v3.model;

/** Execution reads the admitted sowing intent; it never invents an economic area target. */
public final class FieldWorkDemand {
    public static boolean requested(FrontierWorldState state, ResourceSite site, long atTick) {
        var cycle = state.resourceSites().cycle(site.id());
        // Soil preparation/sowing already have an admitted cultivation intent;
        // an empty/immature work pool needs no market-wide production assessment.
        boolean matureWork = false;
        for (var cell : cycle.layout().cells()) {
            if (!cycle.workAvailable(cell.id())) continue;
            if (cycle.cell(cell.id()).crop() != ResourceFieldCycle.Crop.MATURE) return true;
            matureWork = true;
        }
        return matureWork && ProductionStockDemand.read(state, site.settlementId(),
                FieldHarvestExtraction.ITEM, atTick).requested();
    }

    public static boolean allowed(ResourceFieldCycle cycle, ResourceFieldLayout.CellId cell, boolean harvestDemand) {
        return cycle.workAvailable(cell)
                && (cycle.cell(cell).crop() != ResourceFieldCycle.Crop.MATURE || harvestDemand);
    }

    private FieldWorkDemand() { }
}
