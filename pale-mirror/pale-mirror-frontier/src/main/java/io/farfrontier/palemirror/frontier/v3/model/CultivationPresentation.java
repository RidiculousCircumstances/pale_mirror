package io.farfrontier.palemirror.frontier.v3.model;

/** Read-only agriculture labels. Cell biology is separate from work and shipment progress. */
public final class CultivationPresentation {
    public static String plants(ResourceFieldCycle cycle) {
        long mature = cycle.cellStates().values().stream().filter(c -> c.crop() == ResourceFieldCycle.Crop.MATURE).count();
        long growing = cycle.cellStates().values().stream().filter(c -> c.crop() == ResourceFieldCycle.Crop.GROWING).count();
        return mature > 0 ? "RIPE " + mature + " · GROWING " + growing
                : growing > 0 ? "GROWING · STAGE " + cycle.plantGrowthStage() + "/" + ResourceSiteLifecycle.MATURE_STAGE
                : cycle.sowing().cells().isEmpty() ? "FALLOW · LAND IN RESERVE" : "TILL / SOW PENDING";
    }
    public static String work(FrontierWorldState state, ResourceSite site, ResourceSiteLifecycle lifecycle) {
        var cycle = state.resourceSites().cycle(site.id());
        boolean harvest = lifecycle.harvestJobs().values().stream().filter(job -> !job.progress().complete())
                .anyMatch(job -> cycle.cell(cycle.layout().cells().get(job.progress().nextCropSlotIndex()).id()).crop()
                        == ResourceFieldCycle.Crop.MATURE || job.harvestedYieldQuantity() > 0);
        return (harvest ? "HARVEST IN PROGRESS" : "TILL / SOW IN PROGRESS") + " · WORKERS " + lifecycle.harvestJobs().size();
    }
    public static String decision(FrontierWorldState state, ResourceSite site) {
        var review = state.resourceSites().cultivation().get(site.settlementId());
        if (review == null) return "";
        return "\nTOWN PLAN · SOW " + review.selectedCells() + " · PLANTED " + review.plantedCells() + " · RESERVE " + review.reserveCells()
                + "\nYIELD " + review.expectedOutput() + " / TARGET " + review.targetOutput()
                + " · FIRST " + (review.firstHarvestAfterTicks() == Long.MAX_VALUE ? "—" : "~" + review.firstHarvestAfterTicks() + "t")
                + "\n" + review.reason().name().replace('_', ' ');
    }
    private CultivationPresentation() { }
}
