package io.farfrontier.palemirror.frontier.v3.model;

import java.util.*;

/** A farmer commits only its current bounded hand-sized part, not the whole renewable field. */
final class HarvestStockSupply implements StockSupplyPort {
    @Override public Key key() { return Key.FIELD; }
    @Override public long inputDemand(FrontierWorldState state, io.farfrontier.palemirror.frontier.v3.api.SubjectId owner, String itemKind) { return 0; }
    @Override public List<Commitment> pending(FrontierWorldState state) {
        var result = new ArrayList<Commitment>();
        for (var site : state.resourceSites().sites().values()) for (var job : site.harvestJobs().values()) {
            if (job.progress().complete()) continue;
            int remaining = job.returningForBatch() ? (job.batchSuccessorSlot().isPresent() ? ActorCarriedResources.MAX_STACK_ITEMS : 0)
                    : Math.min(ActorCarriedResources.MAX_STACK_ITEMS - job.undeliveredYieldQuantity(),
                    job.progress().totalCropSlots() - job.progress().completedCropSlots());
            result.add(new Commitment(job.id(), state.resourceSite(job.siteId()).settlementId(), "minecraft:wheat", remaining, 0));
        }
        return List.copyOf(result);
    }
}
