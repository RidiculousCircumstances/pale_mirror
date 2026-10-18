package io.farfrontier.palemirror.frontier.v3.model;

/** Execution reads the admitted sowing intent; it never invents an economic area target. */
public final class FieldWorkDemand {
    public static boolean requested(FrontierWorldState state, ResourceSite site) {
        var cycle = state.resourceSites().cycle(site.id());
        boolean harvest = ProductionStockDemand.read(state, site.settlementId(), FieldHarvestExtraction.ITEM).requested();
        return cycle.layout().cells().stream().anyMatch(cell -> allowed(cycle, cell.id(), harvest));
    }

    public static boolean allowed(ResourceFieldCycle cycle, ResourceFieldLayout.CellId cell, boolean harvestDemand) {
        return cycle.workAvailable(cell)
                && (cycle.cell(cell).crop() != ResourceFieldCycle.Crop.MATURE || harvestDemand);
    }

    private FieldWorkDemand() { }
}
